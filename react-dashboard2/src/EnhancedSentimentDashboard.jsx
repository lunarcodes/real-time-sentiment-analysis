import React, { useState, useEffect, useRef, useCallback, useReducer } from 'react';
import * as d3 from 'd3';
import * as THREE from 'three';

/**
 * ============================================================================
 * REAL-TIME SENTIMENT DASHBOARD WITH FULL WEBSOCKET INTEGRATION
 * ============================================================================
 * 
 * FIXED VERSION v3 - Complete fix for live message feed refresh issue
 * 
 * ROOT CAUSE IDENTIFIED:
 * - Multiple flushSync calls were forcing synchronous renders
 * - Subsequent state updates got batched separately by React
 * - Three.js requestAnimationFrame was competing for main thread
 * - This caused the message feed to "stick" and require tab switching
 * 
 * SOLUTION:
 * 1. Removed all flushSync calls - they cause more problems than they solve
 * 2. Implemented MESSAGE BUFFER pattern - messages accumulate in a ref
 * 3. Single requestAnimationFrame loop flushes buffer to state at 60fps
 * 4. All state updates happen together in one batch
 * 5. Three.js runs on separate timing to avoid conflicts
 * 
 * WebSocket updates ALL tabs:
 * - OVERVIEW: sentimentData, historicalData, realtimeMessages, geoData, topCountries
 * - TOPICS: topicsData (from issues_identified in messages)
 * - CHANNELS: channelData (from source.channel in messages)
 * - PRODUCTS: productData (from products_mentioned in messages)
 * - ALERTS: alerts, alertStats (from churn_risk_score > 0.6)
 * 
 * Connect WebSocket server on ws://localhost:8080
 * ============================================================================
 */

// ============================================================================
// TOPIC VOLUME COMPARISON - BAR CHART
// ============================================================================
const TopicVolumeChart = ({ data, colors }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!chartRef.current || data.length === 0) return;

    const container = chartRef.current;
    container.innerHTML = '';

    const margin = { top: 20, right: 30, bottom: 80, left: 60 };
    const width = container.clientWidth - margin.left - margin.right;
    const height = 300 - margin.top - margin.bottom;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`);

    // Sort by count and take top 6
    const topTopics = [...data].sort((a, b) => b.count - a.count).slice(0, 6);

    const x = d3.scaleBand()
      .domain(topTopics.map(d => d.name))
      .range([0, width])
      .padding(0.6);

    const y = d3.scaleLinear()
      .domain([0, d3.max(topTopics, d => d.count) || 100])
      .range([height, 0]);

    // Bars
    svg.selectAll('.bar')
      .data(topTopics)
      .enter()
      .append('rect')
      .attr('class', 'bar')
      .attr('x', d => x(d.name))
      .attr('y', d => y(d.count))
      .attr('width', x.bandwidth())
      .attr('height', d => height - y(d.count))
      .attr('fill', d => d.color)
      .attr('rx', 2);

    // Value labels on top of bars
    svg.selectAll('.label')
      .data(topTopics)
      .enter()
      .append('text')
      .attr('class', 'label')
      .attr('x', d => x(d.name) + x.bandwidth() / 2)
      .attr('y', d => y(d.count) - 5)
      .attr('text-anchor', 'middle')
      .attr('fill', 'white')
      .attr('font-size', '12px')
      .attr('font-weight', 'bold')
      .text(d => d.count);

    // X axis
    svg.append('g')
      .attr('transform', `translate(0,${height})`)
      .call(d3.axisBottom(x))
      .selectAll('text')
      .attr('transform', 'rotate(-45)')
      .style('text-anchor', 'end')
      .style('fill', 'rgba(255,255,255,0.7)')
      .style('font-size', '11px');

    // Y axis
    svg.append('g')
      .call(d3.axisLeft(y).ticks(5))
      .selectAll('text')
      .style('fill', 'rgba(255,255,255,0.7)')
      .style('font-size', '11px');

    // Style axes
    svg.selectAll('.domain, .tick line')
      .style('stroke', 'rgba(255,255,255,0.2)');

  }, [data, colors]);

  return <div ref={chartRef} style={{ width: '100%', height: '300px' }}></div>;
};

// ============================================================================
// TOPIC DISTRIBUTION - DONUT CHART
// ============================================================================
const TopicDistributionChart = ({ data, colors }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!chartRef.current || data.length === 0) return;

    const container = chartRef.current;
    container.innerHTML = '';

    const width = container.clientWidth;
    const height = 300;
    const radius = Math.min(width, height) / 2 - 10;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .append('g')
      .attr('transform', `translate(${width / 2},${height / 2})`);

    // Calculate total and percentages
    const total = data.reduce((sum, d) => sum + d.count, 0);
    const topTopics = [...data].sort((a, b) => b.count - a.count).slice(0, 6);

    const pie = d3.pie()
      .value(d => d.count)
      .sort(null);

    const arc = d3.arc()
      .innerRadius(radius * 0.6)
      .outerRadius(radius);

    const arcs = svg.selectAll('.arc')
      .data(pie(topTopics))
      .enter()
      .append('g')
      .attr('class', 'arc');

    // Slices
    arcs.append('path')
      .attr('d', arc)
      .attr('fill', d => d.data.color)
      .attr('stroke', 'rgba(0,0,0,0.5)')
      .attr('stroke-width', 2);

    // Percentage labels
    arcs.append('text')
      .attr('transform', d => {
        const [x, y] = arc.centroid(d);
        return `translate(${x * 1.4}, ${y * 1.4})`;
      })
      .attr('text-anchor', 'middle')
      .attr('fill', 'white')
      .attr('font-size', '12px')
      .attr('font-weight', 'bold')
      .text(d => {
        const percentage = ((d.data.count / total) * 100).toFixed(0);
        return percentage > 5 ? `${percentage}%` : '';
      });

    // Legend
    const legend = svg.selectAll('.legend')
      .data(topTopics)
      .enter()
      .append('g')
      .attr('class', 'legend')
      .attr('transform', (d, i) => `translate(${radius + 20}, ${-radius + i * 25})`);

    legend.append('rect')
      .attr('width', 15)
      .attr('height', 15)
      .attr('fill', d => d.color)
      .attr('rx', 3);

    legend.append('text')
      .attr('x', 20)
      .attr('y', 12)
      .attr('fill', 'rgba(255,255,255,0.8)')
      .attr('font-size', '10px')
      .text(d => {
        const name = d.name.length > 15 ? d.name.substring(0, 15) + '...' : d.name;
        return `${name} (${d.count})`;
      });

  }, [data, colors]);

  return <div ref={chartRef} style={{ width: '100%', height: '300px' }}></div>;
};

// ============================================================================
// TOPIC INTELLIGENCE - INSIGHT CARDS
// ============================================================================
const TopicIntelligence = ({ data, colors }) => {
  if (data.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px', color: 'rgba(255,255,255,0.5)' }}>
        No topics data available
      </div>
    );
  }

  // Find most critical issue (highest severity + lowest sentiment)
  const criticalIssues = [...data].filter(t => t.severity === 'critical');
  const mostCritical = criticalIssues.length > 0 
    ? criticalIssues.sort((a, b) => a.sentiment - b.sentiment)[0]
    : [...data].sort((a, b) => a.sentiment - b.sentiment)[0];

  // Find fastest growing (most recent increase, could use trend or count)
  const fastestGrowing = [...data].sort((a, b) => {
    const aRecency = new Date(a.lastUpdate).getTime();
    const bRecency = new Date(b.lastUpdate).getTime();
    // Prioritize recent topics with high counts
    return (bRecency * b.count) - (aRecency * a.count);
  })[0];

  // Find most positive (highest sentiment)
  const mostPositive = [...data].sort((a, b) => b.sentiment - a.sentiment)[0];

  const insights = [
    {
      title: 'MOST CRITICAL ISSUE',
      icon: '📱',
      topic: mostCritical.name,
      value: `${mostCritical.count} mentions`,
      color: colors.coral,
      bgColor: 'rgba(255, 107, 157, 0.1)'
    },
    {
      title: 'FASTEST GROWING',
      icon: '⚡',
      topic: fastestGrowing.name,
      value: `+${((fastestGrowing.count / data.reduce((s, d) => s + d.count, 0)) * 100).toFixed(0)}% ↑`,
      color: colors.yellow,
      bgColor: 'rgba(255, 214, 10, 0.1)'
    },
    {
      title: 'MOST POSITIVE',
      icon: '✨',
      topic: mostPositive.name,
      value: `${mostPositive.sentiment.toFixed(0)}% positive`,
      color: colors.green,
      bgColor: 'rgba(0, 255, 159, 0.1)'
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
      {insights.map((insight, idx) => (
        <div key={idx} style={{
          background: insight.bgColor,
          padding: '15px',
          borderRadius: '8px',
          border: `1px solid ${insight.color}30`
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
            <span style={{ fontSize: '20px' }}>{insight.icon}</span>
            <span style={{ fontSize: '10px', color: 'rgba(255,255,255,0.6)', fontWeight: '600', letterSpacing: '0.5px' }}>
              {insight.title}
            </span>
          </div>
          <div style={{ fontSize: '16px', fontWeight: '700', color: insight.color, marginBottom: '4px' }}>
            {insight.topic}
          </div>
          <div style={{ fontSize: '20px', fontWeight: '700', color: insight.color }}>
            {insight.value}
          </div>
        </div>
      ))}
    </div>
  );
};

// ============================================================================
// CHANNEL VOLUME COMPARISON - ROUNDED PROGRESS BARS WITH GLOW
// ============================================================================
const ChannelVolumeChart = ({ data, colors }) => {
  const channelIcons = {
    'social_media': '📱',
    'mobile_app': '📲',
    'website': '🌐',
    'branch': '🏢',
    'call_center': '📞'
  };

  // Sort by volume
  const sortedChannels = [...data]
    .filter(ch => ch.volume > 0)
    .sort((a, b) => b.volume - a.volume);

  if (sortedChannels.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px', color: 'rgba(255,255,255,0.5)' }}>
        No channel data available
      </div>
    );
  }

  const maxVolume = Math.max(...sortedChannels.map(ch => ch.volume), 1);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '15px', padding: '10px 0' }}>
      {sortedChannels.map((channel, index) => {
        const percentage = ((channel.volume / maxVolume) * 100);
        const volumePercentage = ((channel.volume / data.reduce((sum, ch) => sum + ch.volume, 0)) * 100).toFixed(0);
        
        return (
          <div key={channel.id} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {/* Progress Bar Container */}
            <div style={{ position: 'relative', height: '15px' }}>
              {/* Background Track */}
              <div style={{
                position: 'absolute',
                width: '100%',
                height: '15px',
                background: 'rgba(255,255,255,0.05)',
                borderRadius: '8px',
                overflow: 'hidden'
              }}>
                {/* Filled Progress Bar with Gradient */}
                <div
                  style={{
                    width: `${percentage}%`,
                    height: '100%',
                    background: `linear-gradient(90deg, ${channel.color}40, ${channel.color})`,
                    borderRadius: '8px',
                    position: 'relative',
                    transition: 'width 0.8s cubic-bezier(0.4, 0, 0.2, 1)',
                    boxShadow: `0 0 20px ${channel.color}60, inset 0 0 20px ${channel.color}40`,
                    animation: `pulse-${index} 2s ease-in-out infinite`
                  }}
                >
                  {/* Glowing Circle Indicator */}
                  <div
                    style={{
                      position: 'absolute',
                      right: '-10px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      width: '20px',
                      height: '20px',
                      borderRadius: '50%',
                      background: channel.color,
                      boxShadow: `0 0 20px ${channel.color}, 0 0 35px ${channel.color}80, inset 0 0 10px rgba(255,255,255,0.5)`,
                      animation: `glow-${index} 1.5s ease-in-out infinite alternate`,
                      border: '2px solid rgba(255,255,255,0.3)'
                    }}
                  />
                  
                  {/* Shimmer Effect */}
                  <div
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: '-100px',
                      width: '100px',
                      height: '100%',
                      background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent)',
                      animation: `shimmer-${index} 3s linear infinite`,
                      borderRadius: '8px'
                    }}
                  />
                </div>
              </div>
            </div>

            {/* Label */}
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center',
              paddingLeft: '5px'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '20px' }}>{channelIcons[channel.id] || '📊'}</span>
                <span style={{ 
                  fontSize: '14px', 
                  fontWeight: '500', 
                  color: 'rgba(255,255,255,0.9)' 
                }}>
                  {channel.name} - {volumePercentage}% Volume
                </span>
              </div>
            </div>

            {/* CSS Animations */}
            <style jsx>{`
              @keyframes pulse-${index} {
                0%, 100% { opacity: 0.9; }
                50% { opacity: 1; }
              }
              @keyframes glow-${index} {
                0% { 
                  box-shadow: 0 0 20px ${channel.color}, 0 0 40px ${channel.color}80, inset 0 0 15px rgba(255,255,255,0.5);
                  transform: translateY(-50%) scale(1);
                }
                100% { 
                  box-shadow: 0 0 30px ${channel.color}, 0 0 60px ${channel.color}, inset 0 0 20px rgba(255,255,255,0.8);
                  transform: translateY(-50%) scale(1.1);
                }
              }
              @keyframes shimmer-${index} {
                0% { left: -100px; }
                100% { left: 100%; }
              }
            `}</style>
          </div>
        );
      })}
    </div>
  );
};

// ============================================================================
// CHANNEL SENTIMENT SCORE - PILL SEGMENTS STYLE
// ============================================================================
const ChannelSentimentScore = ({ data, colors }) => {
  const channelIcons = {
    'social_media': '📱',
    'mobile_app': '📲',
    'website': '🌐',
    'branch': '🏢',
    'call_center': '📞'
  };

  // Sort by sentiment score
  const sortedChannels = [...data]
    .filter(ch => ch.volume > 0)
    .sort((a, b) => b.sentiment - a.sentiment);

  if (sortedChannels.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px', color: 'rgba(255,255,255,0.5)' }}>
        No channel data available
      </div>
    );
  }

  const totalSegments = 10;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '30px', padding: '10px 0' }}>
      {sortedChannels.map((channel, channelIndex) => {
        // Normalize sentiment from -1,1 to 0,100 for display
        const sentimentPercentage = ((channel.sentiment + 1) / 2) * 100;
        const filledSegments = Math.round((sentimentPercentage / 100) * totalSegments);
        
        return (
          <div key={channel.id} style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            {/* Channel Label */}
            <div style={{ 
              minWidth: '100px',
              fontSize: '13px',
              fontWeight: '700',
              color: 'rgba(255,255,255,0.9)',
              textTransform: 'uppercase',
              letterSpacing: '1px'
            }}>
              {channel.name.split(' ')[0]}
            </div>

            {/* Segment Pills */}
            <div style={{ 
              flex: 1,
              display: 'flex', 
              gap: '8px',
              alignItems: 'center'
            }}>
              {Array.from({ length: totalSegments }).map((_, segmentIndex) => {
                const isFilled = segmentIndex < filledSegments;
                
                return (
                  <div
                    key={segmentIndex}
                    style={{
                      flex: 1,
                      height: '35px',
                      borderRadius: '8px',
                      background: isFilled 
                        ? channel.color
                        : 'rgba(255,255,255,0.08)',
                      boxShadow: isFilled 
                        ? `0 0 15px ${channel.color}60, 0 0 25px ${channel.color}40, inset 0 2px 10px rgba(255,255,255,0.3)`
                        : 'none',
                      border: isFilled 
                        ? `1px solid ${channel.color}80`
                        : '1px solid rgba(255,255,255,0.1)',
                      transition: 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
                      animation: isFilled 
                        ? `pulse-segment-${channelIndex}-${segmentIndex} ${1.5 + segmentIndex * 0.1}s ease-in-out infinite`
                        : 'none',
                      position: 'relative',
                      overflow: 'hidden'
                    }}
                  >
                    {/* Inner glow for filled segments */}
                    {isFilled && (
                      <>
                        <div style={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          right: 0,
                          height: '40%',
                          background: 'linear-gradient(180deg, rgba(255,255,255,0.4), transparent)',
                          borderRadius: '8px 8px 0 0'
                        }} />
                        
                        {/* Shimmer effect */}
                        <div style={{
                          position: 'absolute',
                          top: 0,
                          left: '-100%',
                          width: '100%',
                          height: '100%',
                          background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent)',
                          animation: `shimmer-segment-${channelIndex}-${segmentIndex} 3s linear infinite`,
                          animationDelay: `${segmentIndex * 0.2}s`
                        }} />
                      </>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Percentage Label */}
            <div style={{ 
              minWidth: '60px',
              fontSize: '20px',
              fontWeight: '700',
              color: channel.color,
              textAlign: 'right',
              textShadow: `0 0 20px ${channel.color}80`,
              animation: `text-glow-${channelIndex} 2s ease-in-out infinite`
            }}>
              {sentimentPercentage.toFixed(0)}%
            </div>

            {/* CSS Animations for this channel */}
            <style jsx>{`
              ${Array.from({ length: totalSegments }).map((_, segmentIndex) => `
                @keyframes pulse-segment-${channelIndex}-${segmentIndex} {
                  0%, 100% { 
                    opacity: 0.85;
                    transform: scale(1);
                  }
                  50% { 
                    opacity: 1;
                    transform: scale(1.03);
                  }
                }
                @keyframes shimmer-segment-${channelIndex}-${segmentIndex} {
                  0% { left: -100%; }
                  100% { left: 100%; }
                }
              `).join('\n')}
              
              @keyframes text-glow-${channelIndex} {
                0%, 100% { 
                  text-shadow: 0 0 10px ${channel.color}80;
                  opacity: 0.9;
                }
                50% { 
                  text-shadow: 0 0 20px ${channel.color}, 0 0 30px ${channel.color}80;
                  opacity: 1;
                }
              }
            `}</style>
          </div>
        );
      })}
    </div>
  );
};

// ============================================================================
// PRODUCT SENTIMENT RADAR - PENTAGON CHART
// ============================================================================
const ProductSentimentRadar = ({ data, colors }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!chartRef.current || data.length === 0) return;

    const container = chartRef.current;
    container.innerHTML = '';

    const width = container.clientWidth;
    const height = 400;
    const centerX = width / 2;
    const centerY = height / 2;
    const radius = Math.min(width, height) / 2 - 60;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width)
      .attr('height', height);

    // Take first 5 products for pentagon
    const products = data.slice(0, 5);
    const angleSlice = (Math.PI * 2) / products.length;

    // Normalize sentiment to 0-100 scale for radar
    const maxValue = 100;

    // Create radial scale
    const rScale = d3.scaleLinear()
      .domain([0, maxValue])
      .range([0, radius]);

    // Draw circular grid lines
    const levels = 5;
    for (let i = 1; i <= levels; i++) {
      const levelRadius = (radius / levels) * i;
      
      svg.append('circle')
        .attr('cx', centerX)
        .attr('cy', centerY)
        .attr('r', levelRadius)
        .attr('fill', 'none')
        .attr('stroke', 'rgba(255,255,255,0.1)')
        .attr('stroke-width', 1);
    }

    // Draw axis lines from center to each product point
    products.forEach((product, i) => {
      const angle = angleSlice * i - Math.PI / 2;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius;

      svg.append('line')
        .attr('x1', centerX)
        .attr('y1', centerY)
        .attr('x2', x)
        .attr('y2', y)
        .attr('stroke', 'rgba(255,255,255,0.15)')
        .attr('stroke-width', 1);
    });

    // Prepare data points
    const radarData = products.map((product, i) => {
      const angle = angleSlice * i - Math.PI / 2;
      const sentimentValue = ((product.value + 1) / 2) * 100; // Convert -1,1 to 0,100
      const r = rScale(sentimentValue);
      
      return {
        x: centerX + Math.cos(angle) * r,
        y: centerY + Math.sin(angle) * r,
        product: product,
        angle: angle,
        value: sentimentValue
      };
    });

    // Add glow filter
    const defs = svg.append('defs');
    const filter = defs.append('filter')
      .attr('id', 'radar-glow')
      .attr('x', '-50%')
      .attr('y', '-50%')
      .attr('width', '200%')
      .attr('height', '200%');
    
    filter.append('feGaussianBlur')
      .attr('stdDeviation', '8')
      .attr('result', 'coloredBlur');
    
    const feMerge = filter.append('feMerge');
    feMerge.append('feMergeNode').attr('in', 'coloredBlur');
    feMerge.append('feMergeNode').attr('in', 'SourceGraphic');

    // Draw filled area (polygon)
    const lineGenerator = d3.line()
      .x(d => d.x)
      .y(d => d.y)
      .curve(d3.curveLinearClosed);

    const area = svg.append('path')
      .datum(radarData)
      .attr('d', lineGenerator)
      .attr('fill', colors.cyan)
      .attr('fill-opacity', 0.3)
      .attr('stroke', colors.cyan)
      .attr('stroke-width', 3)
      .attr('filter', 'url(#radar-glow)')
      .style('opacity', 0);

    // Animate the area appearing
    area.transition()
      .duration(1000)
      .style('opacity', 1);

    // Add pulsing animation
    function pulseRadar() {
      area.transition()
        .duration(2000)
        .attr('fill-opacity', 0.5)
        .attr('stroke-width', 4)
        .transition()
        .duration(2000)
        .attr('fill-opacity', 0.3)
        .attr('stroke-width', 3)
        .on('end', pulseRadar);
    }
    pulseRadar();

    // Draw data points
    radarData.forEach((point, i) => {
      const circle = svg.append('circle')
        .attr('cx', centerX)
        .attr('cy', centerY)
        .attr('r', 8)
        .attr('fill', colors.cyan)
        .attr('stroke', 'white')
        .attr('stroke-width', 2)
        .attr('filter', 'url(#radar-glow)');

      // Animate to position
      circle.transition()
        .duration(800)
        .delay(i * 100)
        .attr('cx', point.x)
        .attr('cy', point.y);

      // Pulsing animation for points
      function pulsePoint() {
        d3.select(circle.node())
          .transition()
          .duration(1500)
          .attr('r', 10)
          .transition()
          .duration(1500)
          .attr('r', 8)
          .on('end', pulsePoint);
      }
      setTimeout(() => pulsePoint(), 800 + i * 100);
    });

    // Add labels
    products.forEach((product, i) => {
      const angle = angleSlice * i - Math.PI / 2;
      const labelRadius = radius + 30;
      const x = centerX + Math.cos(angle) * labelRadius;
      const y = centerY + Math.sin(angle) * labelRadius;

      svg.append('text')
        .attr('x', x)
        .attr('y', y)
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('fill', 'rgba(255,255,255,0.8)')
        .attr('font-size', '12px')
        .attr('font-weight', '600')
        .style('opacity', 0)
        .text(product.name)
        .transition()
        .delay(800)
        .duration(500)
        .style('opacity', 1);
    });

  }, [data, colors]);

  return <div ref={chartRef} style={{ width: '100%', height: '400px' }}></div>;
};

// ============================================================================
// CHURN RISK BY PRODUCT - BAR CHART
// ============================================================================
const ChurnRiskByProduct = ({ data, colors }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!chartRef.current || data.length === 0) return;

    const container = chartRef.current;
    container.innerHTML = '';

    const margin = { top: 20, right: 30, bottom: 80, left: 60 };
    const width = container.clientWidth - margin.left - margin.right;
    const height = 400 - margin.top - margin.bottom;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`);

    // Sort by churn risk
    const sortedProducts = [...data].sort((a, b) => b.churnRisk - a.churnRisk);

    const x = d3.scaleBand()
      .domain(sortedProducts.map(d => d.name))
      .range([0, width])
      .padding(0.3);

    const y = d3.scaleLinear()
      .domain([0, 100])
      .range([height, 0]);

    // Add glow filter
    const defs = svg.append('defs');
    const filter = defs.append('filter')
      .attr('id', 'bar-glow')
      .attr('x', '-50%')
      .attr('y', '-50%')
      .attr('width', '200%')
      .attr('height', '200%');
    
    filter.append('feGaussianBlur')
      .attr('stdDeviation', '4')
      .attr('result', 'coloredBlur');
    
    const feMerge = filter.append('feMerge');
    feMerge.append('feMergeNode').attr('in', 'coloredBlur');
    feMerge.append('feMergeNode').attr('in', 'SourceGraphic');

    // Bars
    const bars = svg.selectAll('.bar')
      .data(sortedProducts)
      .enter()
      .append('rect')
      .attr('class', 'bar')
      .attr('x', d => x(d.name))
      .attr('y', height)
      .attr('width', x.bandwidth())
      .attr('height', 0)
      .attr('rx', 6)
      .attr('fill', d => {
        const risk = d.churnRisk * 100;
        if (risk > 60) return colors.coral;
        if (risk > 40) return colors.pink;
        if (risk > 20) return colors.yellow;
        return colors.green;
      })
      .attr('filter', 'url(#bar-glow)');

    // Animate bars growing
    bars.transition()
      .duration(800)
      .delay((d, i) => i * 100)
      .ease(d3.easeCubicOut)
      .attr('y', d => y(d.churnRisk * 100))
      .attr('height', d => height - y(d.churnRisk * 100));

    // Add pulsing animation to bars
    bars.each(function(d, i) {
      d3.select(this)
        .transition()
        .delay(800 + i * 100)
        .duration(1500)
        .style('opacity', 1)
        .transition()
        .duration(1500)
        .style('opacity', 0.85)
        .on('end', function repeat() {
          d3.select(this)
            .transition()
            .duration(1500)
            .style('opacity', 1)
            .transition()
            .duration(1500)
            .style('opacity', 0.85)
            .on('end', repeat);
        });
    });

    // Value labels on top of bars
    const labels = svg.selectAll('.label')
      .data(sortedProducts)
      .enter()
      .append('text')
      .attr('class', 'label')
      .attr('x', d => x(d.name) + x.bandwidth() / 2)
      .attr('y', height)
      .attr('text-anchor', 'middle')
      .attr('fill', 'white')
      .attr('font-size', '16px')
      .attr('font-weight', 'bold')
      .attr('text-shadow', '0 0 10px rgba(0,0,0,0.8)')
      .text(d => `${(d.churnRisk * 100).toFixed(0)}%`)
      .style('opacity', 0);

    labels.transition()
      .duration(500)
      .delay((d, i) => 800 + i * 100)
      .attr('y', d => y(d.churnRisk * 100) - 10)
      .style('opacity', 1);

    // X axis
    svg.append('g')
      .attr('transform', `translate(0,${height})`)
      .call(d3.axisBottom(x))
      .selectAll('text')
      .attr('transform', 'rotate(-45)')
      .style('text-anchor', 'end')
      .style('fill', 'rgba(255,255,255,0.7)')
      .style('font-size', '11px');

    // Y axis
    svg.append('g')
      .call(d3.axisLeft(y).ticks(5).tickFormat(d => d + '%'))
      .selectAll('text')
      .style('fill', 'rgba(255,255,255,0.7)')
      .style('font-size', '11px');

    // Style axes
    svg.selectAll('.domain, .tick line')
      .style('stroke', 'rgba(255,255,255,0.2)');

  }, [data, colors]);

  return <div ref={chartRef} style={{ width: '100%', height: '400px' }}></div>;
};

const EnhancedSentimentDashboard = () => {
  // ============================================================================
  // FORCE UPDATE MECHANISM
  // ============================================================================
  const [updateCounter, setUpdateCounter] = useState(0);

  // ============================================================================
  // WEBSOCKET CONNECTION STATE
  // ============================================================================
  const [isConnected, setIsConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState('Connecting...');
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);

  // ============================================================================
  // VIEW STATE
  // ============================================================================
  const [activeView, setActiveView] = useState('overview');

  // ============================================================================
  // OVERVIEW TAB STATE - Updated by WebSocket
  // ============================================================================
  const [sentimentData, setSentimentData] = useState({
    positive: 0,
    negative: 0,
    neutral: 0,
    overall: 0
  });
  const [historicalData, setHistoricalData] = useState([]);
  const [realtimeMessages, setRealtimeMessages] = useState([]);
  const [geoData, setGeoData] = useState([]);
  const [messagesPerSecond, setMessagesPerSecond] = useState(0);
  const [totalMessages, setTotalMessages] = useState(0);
  const [topCountries, setTopCountries] = useState([]);
  const [continentSentiment, setContinentSentiment] = useState({
    'North America': { positive: 0, negative: 0, neutral: 0, total: 0 },
    'South America': { positive: 0, negative: 0, neutral: 0, total: 0 },
    'Europe': { positive: 0, negative: 0, neutral: 0, total: 0 },
    'Africa': { positive: 0, negative: 0, neutral: 0, total: 0 },
    'Asia': { positive: 0, negative: 0, neutral: 0, total: 0 },
    'Australia': { positive: 0, negative: 0, neutral: 0, total: 0 }
  });

  // ============================================================================
  // TOPICS TAB STATE - Updated by WebSocket (from issues_identified)
  // ============================================================================
  const [topicsData, setTopicsData] = useState([]);

  // ============================================================================
  // CHANNELS TAB STATE - Updated by WebSocket (from source.channel)
  // ============================================================================
  const [channelData, setChannelData] = useState([
    { name: 'Mobile App', id: 'mobile_app', value: 0, color: '#00d9ff', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Website', id: 'website', value: 0, color: '#00ff9f', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Social Media', id: 'social_media', value: 0, color: '#ff006e', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Call Center', id: 'call_center', value: 0, color: '#ff6b9d', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Branch', id: 'branch', value: 0, color: '#ffd60a', sentiment: 0, volume: 0, satisfaction: 0 }
  ]);

  // ============================================================================
  // PRODUCTS TAB STATE - Updated by WebSocket (from products_mentioned)
  // ============================================================================
  const [productData, setProductData] = useState([
    { name: 'Accounts', id: 'accounts', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Credit Cards', id: 'credit_card', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Loans', id: 'loan', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Mortgages', id: 'mortgage', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Investments', id: 'investment', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Savings', id: 'savings_account', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 }
  ]);

  // ============================================================================
  // ALERTS TAB STATE - Updated by WebSocket (from churn_risk_score > 0.6)
  // ============================================================================
  const [alerts, setAlerts] = useState([]);
  const [alertStats, setAlertStats] = useState({
    critical: 0,
    high: 0,
    medium: 0,
    low: 0
  });

  // ============================================================================
  // MESSAGE BUFFER - KEY FIX: Accumulate messages here, flush to state periodically
  // ============================================================================
  const messageBufferRef = useRef([]);
  const pendingUpdatesRef = useRef({
    messages: [],
    totalCount: 0,
    sentimentCounts: { positive: 0, negative: 0, neutral: 0 },
    historicalData: [],
    geoUpdates: [],
    channelUpdates: {},
    productUpdates: {},
    topicUpdates: {},
    alertUpdates: [],
    countryUpdates: {}
  });
  const flushScheduledRef = useRef(false);

  // ============================================================================
  // TRACKING REFS FOR CALCULATIONS
  // ============================================================================
  const messageCountRef = useRef(0);
  const lastRateUpdateRef = useRef(Date.now());
  const sentimentCountsRef = useRef({ positive: 0, negative: 0, neutral: 0 });

  // ============================================================================
  // CHART REFS
  // ============================================================================
  const trendChartRef = useRef(null);
  const globeRef = useRef(null);

  // ============================================================================
  // VISUALIZATION STATE REFS
  // ============================================================================
  const globeSceneRef = useRef(null);
  const globeRendererRef = useRef(null);
  const globeCameraRef = useRef(null);
  const globeMarkersRef = useRef({});
  const globeAnimationRef = useRef(null);
  const globeObjectsRef = useRef({ globe: null, wireframe: null });
  
  const chartInitializedRef = useRef(false);
  const chartSvgRef = useRef(null);
  const chartElementsRef = useRef({});

  // ============================================================================
  // DATA REFS
  // ============================================================================
  const geoDataRef = useRef([]);
  const historicalDataRef = useRef([]);
  const realtimeMessagesRef = useRef([]);
  const topCountriesRef = useRef([]);
  const channelDataRef = useRef([
    { name: 'Mobile App', id: 'mobile_app', value: 0, color: '#00d9ff', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Website', id: 'website', value: 0, color: '#00ff9f', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Social Media', id: 'social_media', value: 0, color: '#ff006e', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Call Center', id: 'call_center', value: 0, color: '#ff6b9d', sentiment: 0, volume: 0, satisfaction: 0 },
    { name: 'Branch', id: 'branch', value: 0, color: '#ffd60a', sentiment: 0, volume: 0, satisfaction: 0 }
  ]);
  const productDataRef = useRef([
    { name: 'Accounts', id: 'accounts', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Credit Cards', id: 'credit_card', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Loans', id: 'loan', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Mortgages', id: 'mortgage', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Investments', id: 'investment', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 },
    { name: 'Savings', id: 'savings_account', value: 0, churnRisk: 0, volume: 0, satisfaction: 0, issues: 0 }
  ]);
  const topicsDataRef = useRef([]);
  const alertsRef = useRef([]);
  const alertStatsRef = useRef({ critical: 0, high: 0, medium: 0, low: 0 });

  // ============================================================================
  // COLORS
  // ============================================================================
  const colors = {
    background: 'linear-gradient(135deg, #0f0f23 0%, #1a1a2e 50%, #16213e 100%)',
    cardBg: 'rgba(26, 26, 46, 0.6)',
    accent: '#00d9ff',
    pink: '#ff006e',
    cyan: '#00d9ff',
    green: '#00ff9f',
    coral: '#ff6b9d',
    yellow: '#ffd60a'
  };

  // ============================================================================
  // CITY COORDINATES MAPPING
  // ============================================================================
  const cityCoordinates = {
    'Dubai': { lat: 25.2048, lon: 55.2708, continent: 'Asia', country: 'UAE', flag: '🇦🇪' },
    'Abu Dhabi': { lat: 24.4539, lon: 54.3773, continent: 'Asia', country: 'UAE', flag: '🇦🇪' },
    'London': { lat: 51.5074, lon: -0.1278, continent: 'Europe', country: 'UK', flag: '🇬🇧' },
    'New York': { lat: 40.7128, lon: -74.0060, continent: 'North America', country: 'USA', flag: '🇺🇸' },
    'Singapore': { lat: 1.3521, lon: 103.8198, continent: 'Asia', country: 'Singapore', flag: '🇸🇬' },
    'Tokyo': { lat: 35.6762, lon: 139.6503, continent: 'Asia', country: 'Japan', flag: '🇯🇵' },
    'Mumbai': { lat: 19.0760, lon: 72.8777, continent: 'Asia', country: 'India', flag: '🇮🇳' },
    'Sydney': { lat: -33.8688, lon: 151.2093, continent: 'Australia', country: 'Australia', flag: '🇦🇺' },
    'Paris': { lat: 48.8566, lon: 2.3522, continent: 'Europe', country: 'France', flag: '🇫🇷' },
    'Unknown': { lat: 25.2048, lon: 55.2708, continent: 'Asia', country: 'Unknown', flag: '🌍' }
  };

  // ============================================================================
  // ISSUE CONFIGURATION FOR TOPICS TAB
  // ============================================================================
  const issueConfig = {
    'app_crash': { name: 'APP CRASHES', icon: '📱', color: '#ff6b6b' },
    'login_issue': { name: 'LOGIN PROBLEMS', icon: '🔐', color: '#ffd93d' },
    'transaction_failure': { name: 'TRANSACTION FAILURES', icon: '💳', color: '#ff6b9d' },
    'slow_performance': { name: 'SLOW PERFORMANCE', icon: '⚡', color: '#ff6b9d' },
    'billing_issue': { name: 'BILLING ISSUES', icon: '📊', color: '#ff6b6b' },
    'security_concern': { name: 'SECURITY CONCERNS', icon: '🔒', color: '#ff006e' },
    'poor_service': { name: 'POOR SERVICE', icon: '⚠️', color: '#ffd93d' },
    'technical_error': { name: 'TECHNICAL ERRORS', icon: '🔧', color: '#ff6b9d' }
  };

  // ============================================================================
  // CHANNEL ICONS
  // ============================================================================
  const channelIcons = {
    'social_media': '📱',
    'mobile_app': '📲',
    'website': '🌐',
    'branch': '🏢',
    'call_center': '📞'
  };

  // ============================================================================
  // FLUSH BUFFER TO STATE - Called by requestAnimationFrame
  // This is the KEY FIX - all state updates happen together
  // ============================================================================
  const flushBufferToState = useCallback(() => {
    flushScheduledRef.current = false;
    
    const buffer = messageBufferRef.current;
    if (buffer.length === 0) return;

    // Clear buffer immediately
    messageBufferRef.current = [];

    // Process all buffered messages
    const newMessages = [];
    let newTotalCount = 0;
    
    buffer.forEach(data => {
      const sentiment = data.sentiment || {};
      const businessIntel = data.business_intelligence || {};
      const contentAnalysis = data.content_analysis || {};
      const timestamps = data.timestamps || {};

      const classification = sentiment.classification || 'neutral';
      const score = sentiment.overall_score || 0;
      const confidence = sentiment.confidence || 0.5;
      const churnRisk = businessIntel.churn_risk_score || 0;
      const text = contentAnalysis.text || data.event_id || 'Message received';
      const products = contentAnalysis.products_mentioned || [];
      const issues = contentAnalysis.issues_identified || [];
      const priority = businessIntel.response_priority || 'normal';

      let city = 'Unknown';
      let channel = 'social_media';

      if (data.location && data.location.parsed && data.location.parsed.city) {
        city = data.location.parsed.city;
      }
      if (data.source && data.source.channel) {
        channel = data.source.channel;
      }

      // Count sentiment
      sentimentCountsRef.current[classification]++;
      newTotalCount++;
      messageCountRef.current++;

      // Create message object
      const newMessage = {
        id: data.event_id || `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}_${newTotalCount}`,
        text: text,
        sentiment: classification,
        sentimentScore: score,
        confidence: confidence,
        channel: channel,
        channelIcon: channelIcons[channel] || '📱',
        gender: ['👨', '👩', '🧑'][Math.floor(Math.random() * 3)],
        churnRisk: churnRisk,
        timestamp: new Date(timestamps.analyzed_at || Date.now()),
        city: city,
        products: products,
        issues: issues,
        priority: priority
      };
      newMessages.push(newMessage);

      // Update historical data ref
      const now = new Date();
      const lastEntry = historicalDataRef.current[historicalDataRef.current.length - 1];
      if (lastEntry && (now - new Date(lastEntry.time)) < 2000) {
        if (classification === 'positive') lastEntry.positive++;
        else if (classification === 'negative') lastEntry.negative++;
        else lastEntry.neutral++;
      } else {
        if (historicalDataRef.current.length >= 60) historicalDataRef.current.shift();
        historicalDataRef.current.push({
          time: now,
          positive: classification === 'positive' ? 1 : 0,
          negative: classification === 'negative' ? 1 : 0,
          neutral: classification === 'neutral' ? 1 : 0
        });
      }

      // Update geo data ref
      if (city && cityCoordinates[city]) {
        const coords = cityCoordinates[city];
        const existingGeoIdx = geoDataRef.current.findIndex(g => g.name === city);
        if (existingGeoIdx >= 0) {
          geoDataRef.current[existingGeoIdx].sentiment = classification;
          geoDataRef.current[existingGeoIdx].value++;
          geoDataRef.current[existingGeoIdx].lastScore = score;
        } else {
          geoDataRef.current.push({
            name: city,
            lat: coords.lat,
            lon: coords.lon,
            sentiment: classification,
            value: 1,
            continent: coords.continent,
            lastScore: score
          });
        }

        // Update top countries ref
        const country = coords.country;
        const existingCountryIdx = topCountriesRef.current.findIndex(c => c.name === country);
        if (existingCountryIdx >= 0) {
          const c = topCountriesRef.current[existingCountryIdx];
          const newVolume = c.volume + 1;
          const newSentiment = (c.sentiment * c.volume + score) / newVolume;
          topCountriesRef.current[existingCountryIdx] = {
            ...c,
            sentiment: newSentiment,
            volume: newVolume,
            trend: newSentiment > c.sentiment ? 'up' : newSentiment < c.sentiment ? 'down' : 'stable'
          };
          console.log(`📊 Country updated: ${coords.flag} ${country} - Vol: ${newVolume}, Sentiment: ${(newSentiment*100).toFixed(1)}%`);
        } else {
          topCountriesRef.current.push({
            name: country,
            sentiment: score,
            volume: 1,
            trend: 'up',
            flag: coords.flag
          });
          console.log(`🆕 New country added: ${coords.flag} ${country} - Sentiment: ${(score*100).toFixed(1)}%`);
        }
        topCountriesRef.current.sort((a, b) => b.volume - a.volume);
        topCountriesRef.current = topCountriesRef.current.slice(0, 10);

        // Update globe marker if scene exists
        if (globeSceneRef.current) {
          updateGlobeMarker(city, classification);
        }
      }

      // Update channel data ref
      channelDataRef.current = channelDataRef.current.map(ch => {
        const channelMatches = ch.id === channel || 
          ch.name.toLowerCase().replace(/\s+/g, '_') === channel ||
          channel.includes(ch.id.split('_')[0]);
        
        if (channelMatches) {
          const newVolume = ch.volume + 1;
          const newSentiment = ch.volume > 0 ? (ch.sentiment * ch.volume + score) / newVolume : score;
          const newSatisfaction = ch.volume > 0 ? (ch.satisfaction * ch.volume + (score + 1) / 2) / newVolume : (score + 1) / 2;
          return {
            ...ch,
            volume: newVolume,
            sentiment: newSentiment,
            satisfaction: newSatisfaction,
            value: Math.min(100, (newVolume / 50) * 100)
          };
        }
        return ch;
      });

      // Update product data ref
      if (products.length > 0) {
        productDataRef.current = productDataRef.current.map(prod => {
          const productMatches = products.some(p => 
            p === prod.id || 
            p.includes(prod.id.split('_')[0]) ||
            prod.id.includes(p.split('_')[0]) ||
            p.toLowerCase() === prod.name.toLowerCase()
          );
          
          if (productMatches) {
            const newVolume = prod.volume + 1;
            const newSentiment = prod.volume > 0 ? (prod.value * prod.volume + score) / newVolume : score;
            const newChurnRisk = prod.volume > 0 ? (prod.churnRisk * prod.volume + churnRisk) / newVolume : churnRisk;
            const newSatisfaction = (newSentiment + 1) / 2;
            const newIssues = prod.issues + (score < -0.3 ? 1 : 0);
            return {
              ...prod,
              volume: newVolume,
              value: newSentiment,
              churnRisk: newChurnRisk,
              satisfaction: newSatisfaction,
              issues: newIssues
            };
          }
          return prod;
        });
      }

      // Update topics data ref
      if (issues.length > 0) {
        issues.forEach(issue => {
          const config = issueConfig[issue] || { 
            name: issue.toUpperCase().replace(/_/g, ' '), 
            icon: '⚠️', 
            color: colors.coral 
          };
          const existingIdx = topicsDataRef.current.findIndex(t => t.id === issue);
          
          if (existingIdx >= 0) {
            const existing = topicsDataRef.current[existingIdx];
            existing.count++;
            existing.sentiment = (existing.sentiment * (existing.count - 1) + (score + 1) * 50) / existing.count;
            existing.lastUpdate = new Date();
            existing.trending = existing.count > 5;
            existing.trendDirection = existing.sentiment > 50 ? 'up' : 'down';
            existing.severity = churnRisk > 0.7 ? 'critical' : churnRisk > 0.5 ? 'high' : churnRisk > 0.3 ? 'medium' : 'low';
          } else {
            topicsDataRef.current.push({
              id: issue,
              name: config.name,
              count: 1,
              sentiment: (score + 1) * 50,
              trending: false,
              trendDirection: score > 0 ? 'up' : 'down',
              color: config.color,
              icon: config.icon,
              severity: churnRisk > 0.7 ? 'critical' : churnRisk > 0.5 ? 'high' : churnRisk > 0.3 ? 'medium' : 'low',
              lastUpdate: new Date()
            });
          }
        });
        topicsDataRef.current.sort((a, b) => b.count - a.count);
        topicsDataRef.current = topicsDataRef.current.slice(0, 12);
      }

      // Update alerts ref
      if (churnRisk > 0.6 || priority === 'urgent' || priority === 'high') {
        const severity = churnRisk > 0.8 ? 'critical' : churnRisk > 0.6 ? 'high' : priority === 'urgent' ? 'high' : 'medium';
        
        const newAlert = {
          id: `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          severity: severity,
          type: issues.length > 0 ? issues[0].replace(/_/g, ' ') : 'churn_risk',
          message: `${severity === 'critical' ? '🚨' : '⚠️'} ${text.substring(0, 100)}${text.length > 100 ? '...' : ''}`,
          channel: channel,
          channelIcon: channelIcons[channel] || '📱',
          churnRisk: churnRisk,
          timestamp: new Date(),
          acknowledged: false,
          city: city,
          priority: priority,
          sentiment: classification,
          products: products
        };

        alertsRef.current = [newAlert, ...alertsRef.current].slice(0, 50);
        alertStatsRef.current[severity]++;
      }
    });

    // Update realtime messages ref
    realtimeMessagesRef.current = [...newMessages, ...realtimeMessagesRef.current].slice(0, 20);

    // Calculate sentiment percentages
    const total = sentimentCountsRef.current.positive + sentimentCountsRef.current.negative + sentimentCountsRef.current.neutral;
    const sentimentPercentages = total > 0 ? {
      positive: (sentimentCountsRef.current.positive / total) * 100,
      negative: (sentimentCountsRef.current.negative / total) * 100,
      neutral: (sentimentCountsRef.current.neutral / total) * 100,
      overall: newMessages.length > 0 ? newMessages[0].sentimentScore : 0
    } : { positive: 0, negative: 0, neutral: 0, overall: 0 };

    // NOW UPDATE ALL STATE AT ONCE - This ensures React batches everything properly
    setTotalMessages(prev => prev + newTotalCount);
    setRealtimeMessages([...realtimeMessagesRef.current]);
    setSentimentData(sentimentPercentages);
    setHistoricalData([...historicalDataRef.current]);
    setGeoData([...geoDataRef.current]);
    setTopCountries([...topCountriesRef.current]);
    console.log(`🌍 TOP COUNTRIES UPDATE:`, {
      count: topCountriesRef.current.length,
      countries: topCountriesRef.current.map(c => ({
        name: c.name,
        flag: c.flag,
        volume: c.volume,
        sentiment: (c.sentiment * 100).toFixed(1) + '%'
      }))
    });
    setChannelData([...channelDataRef.current]);
    setProductData([...productDataRef.current]);
    setTopicsData([...topicsDataRef.current]);
    setAlerts([...alertsRef.current]);
    setAlertStats({...alertStatsRef.current});
    
    // Force update counter to ensure re-render
    setUpdateCounter(prev => prev + 1);

    // Update chart if initialized
    if (chartInitializedRef.current) {
      updateChart();
    }

    console.log(`✅ FLUSH COMPLETE - Processed ${buffer.length} messages:`, {
      totalMessages: realtimeMessagesRef.current.length,
      topCountries: topCountriesRef.current.length,
      geoMarkers: geoDataRef.current.length,
      globeScene: !!globeSceneRef.current,
      activeMarkers: Object.keys(globeMarkersRef.current).length
    });
  }, []);

  // ============================================================================
  // UPDATE GLOBE MARKER - Enhanced with better visibility and logging
  // ============================================================================
  const updateGlobeMarker = useCallback((cityName, sentiment) => {
    if (!globeSceneRef.current) {
      console.warn(`⚠️ Cannot add marker for ${cityName} - globe scene not initialized`);
      return;
    }
    
    if (!cityCoordinates[cityName]) {
      console.warn(`⚠️ No coordinates for city: ${cityName}`);
      return;
    }

    const coords = cityCoordinates[cityName];
    const phi = (90 - coords.lat) * (Math.PI / 180);
    const theta = (coords.lon + 180) * (Math.PI / 180);
    const x = -5.3 * Math.sin(phi) * Math.cos(theta);
    const y = 5.3 * Math.cos(phi);
    const z = 5.3 * Math.sin(phi) * Math.sin(theta);

    const markerColor = sentiment === 'positive' ? 0x00ff00 : sentiment === 'negative' ? 0xff0000 : 0x00ffff;

    if (globeMarkersRef.current[cityName]) {
      // Update existing marker
      const marker = globeMarkersRef.current[cityName];
      marker.material.color.setHex(markerColor);
      
      // Big flash on update
      marker.scale.setScalar(4.0);
      setTimeout(() => { 
        if (marker) marker.scale.setScalar(2.0); 
      }, 300);
      
      console.log(`📍 UPDATED: ${cityName} → ${sentiment} (${markerColor.toString(16)})`);
    } else {
      // Create new marker - VERY LARGE
      const markerGeometry = new THREE.SphereGeometry(0.3, 16, 16);  // Even larger!
      const markerMaterial = new THREE.MeshBasicMaterial({ 
        color: markerColor
      });
      const marker = new THREE.Mesh(markerGeometry, markerMaterial);
      marker.position.set(x, y, z);
      marker.scale.setScalar(2.0);  // Start large
      marker.userData = { city: cityName, basePosition: { x, y, z } };
      globeSceneRef.current.add(marker);
      globeMarkersRef.current[cityName] = marker;
      
      // Strong pulsing animation
      let scale = 2.0;
      let growing = true;
      const pulseInterval = setInterval(() => {
        if (!marker || !marker.scale) {
          clearInterval(pulseInterval);
          return;
        }
        if (growing) {
          scale += 0.05;
          if (scale >= 2.5) growing = false;
        } else {
          scale -= 0.05;
          if (scale <= 1.5) growing = true;
        }
        marker.scale.setScalar(scale);
      }, 50);
      
      marker.userData.pulseInterval = pulseInterval;
      
      console.log(`✨ NEW MARKER: ${cityName} at (${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)}) - ${sentiment} - Color: ${markerColor.toString(16)}`);
    }
  }, []);

  // ============================================================================
  // UPDATE CHART
  // ============================================================================
  const updateChart = useCallback(() => {
    if (!chartElementsRef.current.svg || historicalDataRef.current.length < 2) return;

    const data = historicalDataRef.current;
    const { svg, x, y, width, height } = chartElementsRef.current;

    x.domain(d3.extent(data, d => new Date(d.time)));
    y.domain([0, d3.max(data, d => Math.max(d.positive, d.negative, d.neutral)) || 10]);

    const linePositive = d3.line().x(d => x(new Date(d.time))).y(d => y(d.positive)).curve(d3.curveMonotoneX);
    const lineNegative = d3.line().x(d => x(new Date(d.time))).y(d => y(d.negative)).curve(d3.curveMonotoneX);
    const lineNeutral = d3.line().x(d => x(new Date(d.time))).y(d => y(d.neutral)).curve(d3.curveMonotoneX);

    svg.select('.line-positive').datum(data).attr('d', linePositive);
    svg.select('.line-negative').datum(data).attr('d', lineNegative);
    svg.select('.line-neutral').datum(data).attr('d', lineNeutral);

    svg.select('.x-axis').call(d3.axisBottom(x).ticks(5).tickFormat(d3.timeFormat('%H:%M:%S')));
    svg.select('.y-axis').call(d3.axisLeft(y).ticks(5));
  }, []);

  // ============================================================================
  // SCHEDULE BUFFER FLUSH - Called when new message arrives
  // ============================================================================
  const scheduleFlush = useCallback(() => {
    if (!flushScheduledRef.current) {
      flushScheduledRef.current = true;
      requestAnimationFrame(flushBufferToState);
    }
  }, [flushBufferToState]);

  // ============================================================================
  // PROCESS SENTIMENT MESSAGE - Just adds to buffer, doesn't update state directly
  // ============================================================================
  const processSentimentMessage = useCallback((data) => {
    messageBufferRef.current.push(data);
    scheduleFlush();
  }, [scheduleFlush]);

  // ============================================================================
  // PROCESS AGGREGATED MESSAGE
  // ============================================================================
  const processAggregatedMessage = useCallback((data) => {
    try {
      if (data.sentiment_distribution) {
        const dist = data.sentiment_distribution;
        setSentimentData(prev => ({
          ...prev,
          positive: dist.positive?.percentage || prev.positive,
          negative: dist.negative?.percentage || prev.negative,
          neutral: dist.neutral?.percentage || prev.neutral
        }));
      }

      if (data.metrics) {
        setMessagesPerSecond(data.metrics.messages_per_second || 0);
      }

      if (data.business_metrics) {
        const bm = data.business_metrics;
        setAlertStats(prev => ({
          critical: bm.high_churn_risk_count || prev.critical,
          high: bm.urgent_response_required || prev.high,
          medium: bm.medium_churn_risk_count || prev.medium,
          low: bm.low_churn_risk_count || prev.low
        }));
      }

      console.log('📊 Processed aggregation update');
    } catch (error) {
      console.error('Error processing aggregated message:', error);
    }
  }, []);

  // ============================================================================
  // REFS FOR CALLBACKS
  // ============================================================================
  const processSentimentMessageRef = useRef(processSentimentMessage);
  const processAggregatedMessageRef = useRef(processAggregatedMessage);

  useEffect(() => {
    processSentimentMessageRef.current = processSentimentMessage;
  }, [processSentimentMessage]);

  useEffect(() => {
    processAggregatedMessageRef.current = processAggregatedMessage;
  }, [processAggregatedMessage]);

  // ============================================================================
  // WEBSOCKET CONNECTION
  // ============================================================================
  useEffect(() => {
    let isActive = true;

    const connectWebSocket = () => {
      if (!isActive) return;

      try {
        console.log('🔌 Connecting to WebSocket...');
        const ws = new WebSocket('ws://localhost:8080');
        wsRef.current = ws;

        ws.onopen = () => {
          if (!isActive) return;
          console.log('✅ WebSocket connected successfully');
          setIsConnected(true);
          setConnectionStatus('Connected');
        };

        ws.onmessage = (event) => {
          if (!isActive) return;
          
          try {
            const data = JSON.parse(event.data);
            
            if (data.message_type) {
              switch (data.message_type) {
                case 'sentiment_update':
                  if (data.payload) processSentimentMessageRef.current(data.payload);
                  break;
                case 'aggregation_update':
                  if (data.payload) processAggregatedMessageRef.current(data.payload);
                  break;
                case 'heartbeat':
                  console.log('💓 Heartbeat received');
                  break;
                case 'connection_established':
                  console.log('🔗 Server connection confirmed:', data);
                  break;
                default:
                  if (data.payload) {
                    if (data.payload.aggregation_id) {
                      processAggregatedMessageRef.current(data.payload);
                    } else {
                      processSentimentMessageRef.current(data.payload);
                    }
                  }
              }
            } else {
              if (data.aggregation_id || data.window) {
                processAggregatedMessageRef.current(data);
              } else if (data.event_id || data.sentiment) {
                processSentimentMessageRef.current(data);
              }
            }
          } catch (error) {
            console.error('Error parsing WebSocket message:', error);
          }
        };

        ws.onclose = () => {
          if (!isActive) return;
          console.log('❌ WebSocket disconnected');
          setIsConnected(false);
          setConnectionStatus('Reconnecting...');
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = (error) => {
          console.error('WebSocket error:', error);
          setConnectionStatus('Connection Error');
        };

      } catch (error) {
        console.error('Failed to create WebSocket:', error);
        setConnectionStatus('Failed to connect');
        if (isActive) {
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, 5000);
        }
      }
    };

    connectWebSocket();

    return () => {
      isActive = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []);

  // ============================================================================
  // MESSAGE RATE CALCULATION
  // ============================================================================
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      const elapsed = (now - lastRateUpdateRef.current) / 1000;
      if (elapsed > 0) {
        setMessagesPerSecond(messageCountRef.current / elapsed);
        messageCountRef.current = 0;
        lastRateUpdateRef.current = now;
      }
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  // ============================================================================
  // INITIALIZE D3 CHART
  // ============================================================================
  useEffect(() => {
    if (!trendChartRef.current || activeView !== 'overview' || chartInitializedRef.current) return;

    const container = trendChartRef.current;
    container.innerHTML = '';

    const margin = { top: 20, right: 30, bottom: 30, left: 40 };
    const width = container.clientWidth - margin.left - margin.right;
    const height = 180 - margin.top - margin.bottom;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`);

    const x = d3.scaleTime().range([0, width]);
    const y = d3.scaleLinear().range([height, 0]);

    svg.append('path').attr('class', 'line-positive').attr('fill', 'none').attr('stroke', colors.green).attr('stroke-width', 2);
    svg.append('path').attr('class', 'line-negative').attr('fill', 'none').attr('stroke', colors.coral).attr('stroke-width', 2);
    svg.append('path').attr('class', 'line-neutral').attr('fill', 'none').attr('stroke', colors.cyan).attr('stroke-width', 2);

    svg.append('g').attr('class', 'x-axis').attr('transform', `translate(0,${height})`);
    svg.append('g').attr('class', 'y-axis');

    chartElementsRef.current = { svg, x, y, width, height };
    chartInitializedRef.current = true;

    // Initial chart update
    if (historicalDataRef.current.length > 0) {
      updateChart();
    }
  }, [activeView, updateChart]);

  // ============================================================================
  // REINITIALIZE CHART ON TAB SWITCH
  // ============================================================================
  useEffect(() => {
    if (activeView === 'overview' && !chartInitializedRef.current && trendChartRef.current) {
      const container = trendChartRef.current;
      container.innerHTML = '';

      const margin = { top: 20, right: 30, bottom: 30, left: 40 };
      const width = container.clientWidth - margin.left - margin.right;
      const height = 180 - margin.top - margin.bottom;

      const svg = d3.select(container)
        .append('svg')
        .attr('width', width + margin.left + margin.right)
        .attr('height', height + margin.top + margin.bottom)
        .append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

      const x = d3.scaleTime().range([0, width]);
      const y = d3.scaleLinear().range([height, 0]);

      svg.append('path').attr('class', 'line-positive').attr('fill', 'none').attr('stroke', colors.green).attr('stroke-width', 2);
      svg.append('path').attr('class', 'line-negative').attr('fill', 'none').attr('stroke', colors.coral).attr('stroke-width', 2);
      svg.append('path').attr('class', 'line-neutral').attr('fill', 'none').attr('stroke', colors.cyan).attr('stroke-width', 2);

      svg.append('g').attr('class', 'x-axis').attr('transform', `translate(0,${height})`);
      svg.append('g').attr('class', 'y-axis');

      chartElementsRef.current = { svg, x, y, width, height };
      chartInitializedRef.current = true;
      
      updateChart();
    }
  }, [activeView, updateChart]);

  // ============================================================================
  // INITIALIZE 3D GLOBE - Using EdgesGeometry for visible borders
  // ============================================================================
  useEffect(() => {
    if (!globeRef.current || activeView !== 'overview') return;
    
    const container = globeRef.current;
    container.innerHTML = '';

    // Clear any existing scene
    if (globeSceneRef.current) {
      globeSceneRef.current.clear();
      globeSceneRef.current = null;
    }
    if (globeAnimationRef.current) {
      clearInterval(globeAnimationRef.current);
      globeAnimationRef.current = null;
    }
    
    // Clear markers
    Object.values(globeMarkersRef.current).forEach(marker => {
      if (marker.userData.pulseInterval) {
        clearInterval(marker.userData.pulseInterval);
      }
    });
    globeMarkersRef.current = {};

    const width = container.clientWidth || 800;
    const height = 350;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000);
    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    
    renderer.setSize(width, height);
    renderer.setClearColor(0x000000, 0);
    container.appendChild(renderer.domElement);

    globeSceneRef.current = scene;
    globeCameraRef.current = camera;
    globeRendererRef.current = renderer;

    // BASE GLOBE - Dark sphere
    const geometry = new THREE.SphereGeometry(5, 64, 64);
    const material = new THREE.MeshPhongMaterial({ 
      color: 0x1a1a2e, 
      transparent: true, 
      opacity: 0.7,
      shininess: 20
    });
    const globe = new THREE.Mesh(geometry, material);
    scene.add(globe);
    globeObjectsRef.current.globe = globe;

    // COUNTRY BORDERS - Using LineSegments with EdgesGeometry for MAXIMUM VISIBILITY
    const bordersGeometry = new THREE.EdgesGeometry(new THREE.SphereGeometry(5.1, 32, 32));
    const bordersMaterial = new THREE.LineBasicMaterial({ 
      color: 0x00ffff,  // Bright cyan
      linewidth: 3,      // Thick lines
      transparent: false,
      opacity: 1.0       // Fully opaque
    });
    const borders = new THREE.LineSegments(bordersGeometry, bordersMaterial);
    scene.add(borders);
    globeObjectsRef.current.borders = borders;
    console.log('✅ Country borders added with EdgesGeometry');

    // GRID LINES - Using LineSegments for latitude/longitude
    const gridGeometry = new THREE.EdgesGeometry(new THREE.SphereGeometry(5.15, 32, 16));
    const gridMaterial = new THREE.LineBasicMaterial({ 
      color: 0x00ff00,   // Bright green
      linewidth: 2,
      transparent: true,
      opacity: 0.6
    });
    const grid = new THREE.LineSegments(gridGeometry, gridMaterial);
    scene.add(grid);
    globeObjectsRef.current.grid = grid;
    console.log('✅ Grid lines added with EdgesGeometry');

    // LIGHTING
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    scene.add(ambientLight);
    
    const pointLight1 = new THREE.PointLight(0xffffff, 1);
    pointLight1.position.set(10, 10, 10);
    scene.add(pointLight1);
    
    const pointLight2 = new THREE.PointLight(0xffffff, 0.5);
    pointLight2.position.set(-10, -10, -10);
    scene.add(pointLight2);

    camera.position.z = 15;  // Moved back for better view

    console.log('🌍 Globe initialization complete - starting animation');

    // ANIMATION LOOP - 30 FPS
    const animationInterval = setInterval(() => {
      if (globeObjectsRef.current.globe) {
        globeObjectsRef.current.globe.rotation.y += 0.003;
      }
      if (globeObjectsRef.current.borders) {
        globeObjectsRef.current.borders.rotation.y += 0.003;
      }
      if (globeObjectsRef.current.grid) {
        globeObjectsRef.current.grid.rotation.y += 0.003;
      }
      
      Object.values(globeMarkersRef.current).forEach(marker => {
        if (marker && marker.position) {
          marker.position.applyAxisAngle(new THREE.Vector3(0, 1, 0), 0.003);
        }
      });
      
      if (renderer && scene && camera) {
        renderer.render(scene, camera);
      }
    }, 1000 / 30);

    globeAnimationRef.current = animationInterval;

    // Add existing markers
    const existingMarkers = geoDataRef.current.length;
    console.log(`🌍 Adding ${existingMarkers} existing markers to globe`);
    geoDataRef.current.forEach(city => {
      updateGlobeMarker(city.name, city.sentiment);
    });

    console.log(`✅✅✅ GLOBE FULLY INITIALIZED - Scene ready, borders visible, ${existingMarkers} markers added`);

    return () => {
      console.log('🧹 Cleaning up globe scene');
      if (globeAnimationRef.current) {
        clearInterval(globeAnimationRef.current);
        globeAnimationRef.current = null;
      }
      Object.values(globeMarkersRef.current).forEach(marker => {
        if (marker.userData.pulseInterval) {
          clearInterval(marker.userData.pulseInterval);
        }
      });
    };
  }, [activeView, updateGlobeMarker]);

  // ============================================================================
  // CLEANUP ON TAB SWITCH
  // ============================================================================
  useEffect(() => {
    if (activeView !== 'overview') {
      if (globeAnimationRef.current) {
        clearInterval(globeAnimationRef.current);
        globeAnimationRef.current = null;
      }
      if (globeRendererRef.current) {
        globeRendererRef.current.dispose();
        globeRendererRef.current = null;
      }
      globeSceneRef.current = null;
      globeCameraRef.current = null;
      globeObjectsRef.current = { globe: null, wireframe: null };
      globeMarkersRef.current = {};
      chartInitializedRef.current = false;
      chartElementsRef.current = {};
    }
  }, [activeView]);

  // ============================================================================
  // RENDER
  // ============================================================================
  return (
    <>
      <style>{`
        @keyframes pulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.05); opacity: 0.8; } }
        @keyframes slideIn { from { opacity: 0; transform: translateX(-20px); } to { opacity: 1; transform: translateX(0); } }
        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        .card { background: ${colors.cardBg}; backdrop-filter: blur(10px); border-radius: 12px; padding: 20px; }
        .scrollbar::-webkit-scrollbar { width: 6px; }
        .scrollbar::-webkit-scrollbar-track { background: rgba(255,255,255,0.05); }
        .scrollbar::-webkit-scrollbar-thumb { background: ${colors.cyan}50; border-radius: 3px; }
        .message-item { animation: fadeIn 0.3s ease-out; }
        .message-item:first-child { animation: slideIn 0.3s ease-out; }
      `}</style>

      <div style={{
        minHeight: '100vh',
        background: colors.background,
        color: 'white',
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: '20px'
      }}>
        {/* HEADER */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '20px',
          padding: '15px 25px',
          background: colors.cardBg,
          backdropFilter: 'blur(10px)',
          borderRadius: '16px',
          border: `1px solid ${colors.cyan}30`
        }}>
          <div>
            <h1 style={{ margin: 0, fontSize: '26px', fontWeight: '700', background: `linear-gradient(135deg, ${colors.cyan}, ${colors.pink})`, WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              Real-Time Sentiment Intelligence
            </h1>
            <p style={{ margin: '5px 0 0', color: 'rgba(255,255,255,0.6)', fontSize: '12px' }}>
              Live streaming analytics • WebSocket updates ALL tabs in real-time
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 16px',
              background: isConnected ? 'rgba(0, 255, 159, 0.1)' : 'rgba(255, 107, 157, 0.1)',
              borderRadius: '20px', border: `1px solid ${isConnected ? colors.green : colors.coral}40`
            }}>
              <div style={{
                width: '8px', height: '8px', borderRadius: '50%',
                background: isConnected ? colors.green : colors.coral,
                animation: isConnected ? 'pulse 2s infinite' : 'none'
              }} />
              <span style={{ fontSize: '11px', color: isConnected ? colors.green : colors.coral, fontWeight: '600' }}>
                {connectionStatus}
              </span>
            </div>

            <div style={{ padding: '8px 16px', background: 'rgba(0, 217, 255, 0.1)', borderRadius: '20px', border: `1px solid ${colors.cyan}40` }}>
              <span style={{ fontSize: '11px', color: colors.cyan, fontWeight: '600' }}>⚡ {messagesPerSecond.toFixed(1)} msg/s</span>
            </div>

            <div style={{ padding: '8px 16px', background: 'rgba(255, 0, 110, 0.1)', borderRadius: '20px', border: `1px solid ${colors.pink}40` }}>
              <span style={{ fontSize: '11px', color: colors.pink, fontWeight: '600' }}>📊 {totalMessages.toLocaleString()} total</span>
            </div>

            {alerts.length > 0 && (
              <div style={{ padding: '8px 16px', background: 'rgba(255, 107, 157, 0.2)', borderRadius: '20px', border: `1px solid ${colors.coral}40`, animation: 'pulse 1s infinite' }}>
                <span style={{ fontSize: '11px', color: colors.coral, fontWeight: '600' }}>🚨 {alerts.filter(a => !a.acknowledged).length} alerts</span>
              </div>
            )}
          </div>
        </div>

        {/* NAVIGATION TABS */}
        <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
          {[
            { id: 'overview', label: 'Overview', icon: '📊', description: 'Sentiment, Globe, Feed' },
            { id: 'topics', label: 'Topics', icon: '💬', description: 'Issues from messages' },
            { id: 'channels', label: 'Channels', icon: '📱', description: 'Channel breakdown' },
            { id: 'products', label: 'Products', icon: '🏦', description: 'Product sentiment' },
            { id: 'alerts', label: 'Alerts', icon: '🚨', description: 'High churn risks', badge: alerts.filter(a => !a.acknowledged).length }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveView(tab.id)}
              title={tab.description}
              style={{
                padding: '12px 24px',
                background: activeView === tab.id ? colors.cyan : 'rgba(255,255,255,0.05)',
                color: activeView === tab.id ? '#000' : 'white',
                border: 'none',
                borderRadius: '10px',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                transition: 'all 0.3s',
                position: 'relative'
              }}
            >
              <span>{tab.icon}</span>
              {tab.label}
              {tab.badge > 0 && (
                <span style={{
                  position: 'absolute', top: '-5px', right: '-5px',
                  background: colors.coral, color: 'white', borderRadius: '50%',
                  width: '20px', height: '20px', fontSize: '10px',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700'
                }}>
                  {tab.badge}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* ================================================================== */}
        {/* OVERVIEW TAB */}
        {/* ================================================================== */}
        {activeView === 'overview' && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: '20px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Sentiment Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '15px' }}>
                {[
                  { label: 'Positive', value: sentimentData.positive, color: colors.green, icon: '😊' },
                  { label: 'Negative', value: sentimentData.negative, color: colors.coral, icon: '😠' },
                  { label: 'Neutral', value: sentimentData.neutral, color: colors.cyan, icon: '😐' }
                ].map(item => (
                  <div key={item.label} className="card" style={{ border: `2px solid ${item.color}30` }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '24px' }}>{item.icon}</span>
                      <span style={{ fontSize: '11px', color: 'rgba(255,255,255,0.6)', fontWeight: '600' }}>{item.label}</span>
                    </div>
                    <div style={{ fontSize: '36px', fontWeight: '700', color: item.color, textShadow: `0 0 20px ${item.color}60`, marginTop: '10px' }}>
                      {item.value.toFixed(1)}%
                    </div>
                    <div style={{ marginTop: '10px', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                      <div style={{ width: `${item.value}%`, height: '100%', background: item.color, borderRadius: '2px', transition: 'width 0.5s' }} />
                    </div>
                  </div>
                ))}
              </div>

              {/* 3D Globe */}
              <div className="card" style={{ border: `1px solid ${colors.cyan}30` }}>
                <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>🌍 Global Sentiment Distribution</h3>
                <div ref={globeRef} style={{ width: '100%', height: '350px' }}></div>
              </div>

              {/* Trend Chart */}
              <div className="card" style={{ border: `1px solid ${colors.pink}30` }}>
                <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📈 Real-Time Sentiment Trend</h3>
                <div ref={trendChartRef} style={{ width: '100%', height: '180px' }}></div>
                <div style={{ display: 'flex', gap: '20px', marginTop: '10px', justifyContent: 'center' }}>
                  <span style={{ fontSize: '11px', color: colors.green }}>● Positive</span>
                  <span style={{ fontSize: '11px', color: colors.coral }}>● Negative</span>
                  <span style={{ fontSize: '11px', color: colors.cyan }}>● Neutral</span>
                </div>
              </div>
            </div>

            {/* Live Feed - KEY: Uses updateCounter to force re-renders */}
            <div className="card scrollbar" key={`feed-${updateCounter}`} style={{ border: `1px solid ${colors.cyan}30`, maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }}>
              <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px', position: 'sticky', top: 0, background: colors.cardBg, paddingBottom: '10px', zIndex: 10 }}>
                📡 Live Message Feed
                <span style={{ marginLeft: '10px', fontSize: '10px', color: colors.cyan, fontWeight: '400' }}>
                  ({realtimeMessages.length} messages)
                </span>
              </h3>
              
              {realtimeMessages.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 20px', color: 'rgba(255,255,255,0.4)' }}>
                  <div style={{ fontSize: '48px', marginBottom: '10px' }}>📡</div>
                  <p>Waiting for messages...</p>
                  <p style={{ fontSize: '12px' }}>Connect WebSocket server on port 8080</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {realtimeMessages.map((msg, idx) => {
                    const sentimentColor = msg.sentiment === 'positive' ? colors.green : msg.sentiment === 'negative' ? colors.coral : colors.cyan;
                    return (
                      <div 
                        key={msg.id} 
                        className="message-item"
                        style={{
                          background: 'rgba(0, 0, 0, 0.3)', 
                          padding: '12px', 
                          borderRadius: '8px',
                          border: `1px solid ${sentimentColor}30`
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span>{msg.gender}</span>
                            <span>{msg.channelIcon}</span>
                            <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '600', background: sentimentColor, color: 'white' }}>
                              {(msg.sentimentScore * 100).toFixed(0)}%
                            </span>
                            {msg.city !== 'Unknown' && <span style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)' }}>📍 {msg.city}</span>}
                          </div>
                          <span style={{ fontSize: '10px', color: 'rgba(255,255,255,0.4)' }}>{new Date(msg.timestamp).toLocaleTimeString()}</span>
                        </div>
                        <p style={{ margin: 0, fontSize: '12px', color: 'rgba(255,255,255,0.8)', lineHeight: '1.4' }}>{msg.text}</p>
                        {msg.churnRisk > 0.5 && (
                          <div style={{ marginTop: '8px', padding: '4px 8px', background: colors.coral + '20', borderRadius: '4px', fontSize: '10px', color: colors.coral, display: 'inline-block' }}>
                            ⚠️ Churn Risk: {(msg.churnRisk * 100).toFixed(0)}%
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Top Countries - Overview Tab - Placed outside main grid */}
          <div className="card" style={{ border: `1px solid ${colors.cyan}30`, marginTop: '20px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>🌍 Top Countries by Volume</h3>
            {topCountries.length > 0 ? (
              <div style={{ display: 'flex', gap: '15px', overflowX: 'auto', paddingBottom: '10px' }}>
                {topCountries.slice(0, 8).map(country => (
                  <div key={country.name} style={{
                    background: 'rgba(0,0,0,0.3)', 
                    padding: '15px 20px', 
                    borderRadius: '8px', 
                    minWidth: '120px', 
                    textAlign: 'center',
                    border: `2px solid ${country.sentiment > 0 ? colors.green : country.sentiment < 0 ? colors.coral : colors.cyan}`,
                    boxShadow: `0 0 20px ${country.sentiment > 0 ? colors.green : country.sentiment < 0 ? colors.coral : colors.cyan}40`
                  }}>
                    <div style={{ fontSize: '36px', marginBottom: '8px' }}>{country.flag}</div>
                    <div style={{ fontSize: '14px', fontWeight: '700', marginBottom: '8px', color: 'white' }}>{country.name}</div>
                    <div style={{ fontSize: '24px', fontWeight: '700', color: country.sentiment > 0 ? colors.green : country.sentiment < 0 ? colors.coral : colors.cyan }}>
                      {(country.sentiment * 100).toFixed(0)}%
                    </div>
                    <div style={{ fontSize: '11px', color: 'rgba(255,255,255,0.6)', marginTop: '4px' }}>{country.volume.toLocaleString()} messages</div>
                  </div>
                ))}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '40px', color: 'rgba(255,255,255,0.5)' }}>
                <div style={{ fontSize: '48px', marginBottom: '10px' }}>🌍</div>
                <p>Waiting for messages with location data...</p>
                <p style={{ fontSize: '12px', marginTop: '8px' }}>Countries will appear as messages arrive</p>
              </div>
            )}
          </div>
          </div>
        )}

        {/* ================================================================== */}
        {/* TOPICS TAB - Updated from issues_identified */}
        {/* ================================================================== */}
        {activeView === 'topics' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '15px' }}>
              {[
                { label: 'Total Topics', value: topicsData.length, color: colors.cyan, icon: '📋' },
                { label: 'Trending', value: topicsData.filter(t => t.trending).length, color: colors.green, icon: '📈' },
                { label: 'Critical Issues', value: topicsData.filter(t => t.severity === 'critical').length, color: colors.coral, icon: '🚨' },
                { label: 'Avg Sentiment', value: topicsData.length > 0 ? (topicsData.reduce((a, t) => a + t.sentiment, 0) / topicsData.length).toFixed(0) + '%' : '0%', color: colors.yellow, icon: '😊' }
              ].map(stat => (
                <div key={stat.label} className="card" style={{ border: `2px solid ${stat.color}30` }}>
                  <div style={{ fontSize: '28px', marginBottom: '8px' }}>{stat.icon}</div>
                  <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.6)' }}>{stat.label}</div>
                  <div style={{ fontSize: '32px', fontWeight: '700', color: stat.color }}>{stat.value}</div>
                </div>
              ))}
            </div>

            {topicsData.length === 0 ? (
              <div className="card" style={{ textAlign: 'center', padding: '60px' }}>
                <div style={{ fontSize: '48px', marginBottom: '15px' }}>💬</div>
                <p style={{ color: 'rgba(255,255,255,0.6)' }}>No topics detected yet. Topics are extracted from issues_identified in incoming messages.</p>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '15px' }}>
                {topicsData.map(topic => {
                  const severityColors = { critical: colors.coral, high: colors.pink, medium: colors.yellow, low: colors.cyan };
                  const severityColor = severityColors[topic.severity] || colors.cyan;
                  
                  return (
                    <div key={topic.id} className="card" style={{ border: `2px solid ${severityColor}30` }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <span style={{ fontSize: '24px' }}>{topic.icon}</span>
                          <div>
                            <div style={{ fontSize: '13px', fontWeight: '600' }}>{topic.name}</div>
                            <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)' }}>{topic.trending && '🔥 Trending'}</div>
                          </div>
                        </div>
                        <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '10px', fontWeight: '700', background: severityColor, color: 'white', textTransform: 'uppercase' }}>
                          {topic.severity}
                        </span>
                      </div>
                      
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
                        <div>
                          <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Mentions</div>
                          <div style={{ fontSize: '24px', fontWeight: '700', color: colors.cyan }}>{topic.count}</div>
                        </div>
                        <div>
                          <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Sentiment</div>
                          <div style={{ fontSize: '24px', fontWeight: '700', color: topic.sentiment > 50 ? colors.green : colors.coral }}>{topic.sentiment.toFixed(0)}%</div>
                        </div>
                      </div>
                      
                      <div style={{ marginTop: '12px', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                        <div style={{ width: `${topic.sentiment}%`, height: '100%', background: topic.sentiment > 50 ? colors.green : colors.coral, borderRadius: '2px', transition: 'width 0.5s' }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Topic Volume Comparison - Bar Chart */}
            {topicsData.length > 0 && (
              <div className="card" style={{ border: `1px solid ${colors.cyan}30` }}>
                <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📊 TOPIC VOLUME COMPARISON</h3>
                <TopicVolumeChart data={topicsData} colors={colors} />
              </div>
            )}

            {/* Topic Distribution and Intelligence Grid */}
            {topicsData.length > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                {/* Topic Distribution - Donut Chart */}
                <div className="card" style={{ border: `1px solid ${colors.pink}30` }}>
                  <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📈 TOPIC DISTRIBUTION</h3>
                  <TopicDistributionChart data={topicsData} colors={colors} />
                </div>

                {/* Topic Intelligence - Insight Cards */}
                <div className="card" style={{ border: `1px solid ${colors.yellow}30` }}>
                  <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>💡 TOPIC INTELLIGENCE</h3>
                  <TopicIntelligence data={topicsData} colors={colors} />
                </div>
              </div>
            )}
          </div>
        )}

        {/* ================================================================== */}
        {/* CHANNELS TAB - Updated from source.channel */}
        {/* ================================================================== */}
        {activeView === 'channels' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '15px' }}>
              {channelData.map(channel => (
                <div key={channel.id} className="card" style={{ border: `2px solid ${channel.color}30` }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <span style={{ fontSize: '24px' }}>{channelIcons[channel.id] || '📱'}</span>
                    <span style={{ fontSize: '11px', color: 'rgba(255,255,255,0.6)', fontWeight: '600' }}>{channel.name}</span>
                  </div>
                  <div style={{ fontSize: '28px', fontWeight: '700', color: channel.color }}>{channel.volume.toLocaleString()}</div>
                  <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginTop: '4px' }}>messages</div>
                  
                  <div style={{ marginTop: '15px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <div>
                      <div style={{ fontSize: '9px', color: 'rgba(255,255,255,0.5)' }}>Sentiment</div>
                      <div style={{ fontSize: '14px', fontWeight: '600', color: channel.sentiment > 0 ? colors.green : channel.sentiment < 0 ? colors.coral : colors.cyan }}>
                        {(channel.sentiment * 100).toFixed(0)}%
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '9px', color: 'rgba(255,255,255,0.5)' }}>Satisfaction</div>
                      <div style={{ fontSize: '14px', fontWeight: '600', color: channel.satisfaction > 0.6 ? colors.green : colors.yellow }}>
                        {(channel.satisfaction * 100).toFixed(0)}%
                      </div>
                    </div>
                  </div>
                  
                  <div style={{ marginTop: '12px', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                    <div style={{ width: `${channel.value}%`, height: '100%', background: channel.color, borderRadius: '2px', transition: 'width 0.5s' }} />
                  </div>
                </div>
              ))}
            </div>

            {/* Channel Comparison */}
            <div className="card" style={{ border: `1px solid ${colors.cyan}30` }}>
              <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '20px' }}>📊 Channel Performance Comparison</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                {[...channelData].sort((a, b) => b.volume - a.volume).map(channel => (
                  <div key={channel.id} style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                    <span style={{ width: '30px', fontSize: '20px' }}>{channelIcons[channel.id]}</span>
                    <div style={{ width: '120px', fontSize: '12px', fontWeight: '500' }}>{channel.name}</div>
                    <div style={{ flex: 1, height: '24px', background: 'rgba(255,255,255,0.05)', borderRadius: '12px', overflow: 'hidden' }}>
                      <div style={{
                        width: `${Math.max(...channelData.map(c => c.volume)) > 0 ? (channel.volume / Math.max(...channelData.map(c => c.volume)) * 100) : 0}%`,
                        height: '100%', background: `linear-gradient(90deg, ${channel.color}, ${channel.color}80)`,
                        borderRadius: '12px', transition: 'width 0.5s', display: 'flex', alignItems: 'center', paddingLeft: '10px'
                      }}>
                        <span style={{ fontSize: '10px', fontWeight: '600', color: 'white' }}>{channel.volume > 0 ? channel.volume.toLocaleString() : ''}</span>
                      </div>
                    </div>
                    <div style={{ width: '80px', textAlign: 'right' }}>
                      <span style={{ fontSize: '12px', fontWeight: '600', color: channel.sentiment > 0 ? colors.green : channel.sentiment < 0 ? colors.coral : colors.cyan }}>
                        {(channel.sentiment * 100).toFixed(0)}%
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Channel Volume Comparison - Bar Chart */}
            {channelData.some(ch => ch.volume > 0) && (
              <div className="card" style={{ border: `1px solid ${colors.cyan}30` }}>
                <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📊 CHANNEL VOLUME COMPARISON</h3>
                <ChannelVolumeChart data={channelData} colors={colors} />
              </div>
            )}

            {/* Channel Sentiment Score - Horizontal Progress Bars */}
            {channelData.some(ch => ch.volume > 0) && (
              <div className="card" style={{ border: `1px solid ${colors.pink}30` }}>
                <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📈 CHANNEL SENTIMENT SCORE</h3>
                <ChannelSentimentScore data={channelData} colors={colors} />
              </div>
            )}
          </div>
        )}

        {/* ================================================================== */}
        {/* PRODUCTS TAB - Updated from products_mentioned */}
        {/* ================================================================== */}
        {activeView === 'products' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '15px' }}>
              {productData.map(product => {
                const sentimentColor = product.value > 0.2 ? colors.green : product.value < -0.2 ? colors.coral : colors.cyan;
                const riskColor = product.churnRisk > 0.6 ? colors.coral : product.churnRisk > 0.3 ? colors.yellow : colors.green;
                
                return (
                  <div key={product.id} className="card" style={{ border: `1px solid ${sentimentColor}30` }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                      <h3 style={{ margin: 0, fontSize: '16px', fontWeight: '600' }}>{product.name}</h3>
                      <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '10px', fontWeight: '600', background: riskColor + '30', color: riskColor }}>
                        Risk: {(product.churnRisk * 100).toFixed(0)}%
                      </span>
                    </div>
                    
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '15px' }}>
                      <div>
                        <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Sentiment</div>
                        <div style={{ fontSize: '24px', fontWeight: '700', color: sentimentColor }}>{(product.value * 100).toFixed(0)}%</div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Volume</div>
                        <div style={{ fontSize: '24px', fontWeight: '700', color: colors.cyan }}>{product.volume.toLocaleString()}</div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Satisfaction</div>
                        <div style={{ fontSize: '18px', fontWeight: '600', color: product.satisfaction > 0.6 ? colors.green : colors.yellow }}>{(product.satisfaction * 100).toFixed(0)}%</div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '4px' }}>Issues</div>
                        <div style={{ fontSize: '18px', fontWeight: '600', color: product.issues > 10 ? colors.coral : colors.cyan }}>{product.issues}</div>
                      </div>
                    </div>
                    
                    <div style={{ marginTop: '15px' }}>
                      <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.5)', marginBottom: '6px' }}>Churn Risk Level</div>
                      <div style={{ height: '8px', background: 'rgba(255,255,255,0.1)', borderRadius: '4px' }}>
                        <div style={{ width: `${product.churnRisk * 100}%`, height: '100%', background: `linear-gradient(90deg, ${colors.green}, ${colors.yellow}, ${colors.coral})`, borderRadius: '4px', transition: 'width 0.5s' }} />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Product Visualizations Grid */}
            {productData.length > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                {/* Product Sentiment Radar */}
                <div className="card" style={{ border: `1px solid ${colors.cyan}30` }}>
                  <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>📡 PRODUCT SENTIMENT RADAR</h3>
                  <ProductSentimentRadar data={productData} colors={colors} />
                </div>

                {/* Churn Risk by Product */}
                <div className="card" style={{ border: `1px solid ${colors.coral}30` }}>
                  <h3 style={{ fontSize: '14px', fontWeight: '600', marginTop: 0, marginBottom: '15px' }}>⚠️ CHURN RISK BY PRODUCT</h3>
                  <ChurnRiskByProduct data={productData} colors={colors} />
                </div>
              </div>
            )}
          </div>
        )}

        {/* ================================================================== */}
        {/* ALERTS TAB - Updated from churn_risk_score > 0.6 */}
        {/* ================================================================== */}
        {activeView === 'alerts' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '15px' }}>
              {[
                { label: 'Critical', count: alertStats.critical, color: colors.coral, icon: '🚨' },
                { label: 'High', count: alertStats.high, color: colors.pink, icon: '⚠️' },
                { label: 'Medium', count: alertStats.medium, color: colors.yellow, icon: '💡' },
                { label: 'Low', count: alertStats.low, color: colors.cyan, icon: 'ℹ️' }
              ].map(stat => (
                <div key={stat.label} className="card" style={{ border: `2px solid ${stat.color}30` }}>
                  <div style={{ fontSize: '32px', marginBottom: '8px' }}>{stat.icon}</div>
                  <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.6)' }}>{stat.label}</div>
                  <div style={{ fontSize: '36px', fontWeight: '700', color: stat.color }}>{stat.count}</div>
                </div>
              ))}
            </div>

            <div className="card scrollbar" style={{ border: `1px solid ${colors.pink}30`, maxHeight: '600px', overflowY: 'auto' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '600', marginTop: 0, marginBottom: '20px', position: 'sticky', top: 0, background: colors.cardBg, paddingBottom: '10px' }}>
                🚨 Recent Alerts ({alerts.length}) - Generated when churn_risk_score &gt; 0.6
              </h3>
              
              {alerts.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'rgba(255,255,255,0.4)' }}>
                  <div style={{ fontSize: '48px', marginBottom: '10px' }}>✅</div>
                  <p>No alerts - All systems normal</p>
                  <p style={{ fontSize: '12px' }}>Alerts are generated when churn_risk_score exceeds 0.6</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {alerts.map((alert, idx) => {
                    const severityColors = { critical: colors.coral, high: colors.pink, medium: colors.yellow, low: colors.cyan };
                    const severityColor = severityColors[alert.severity];

                    return (
                      <div key={alert.id} style={{
                        background: 'rgba(0, 0, 0, 0.3)', padding: '16px', borderRadius: '8px',
                        border: `2px solid ${severityColor}30`, animation: idx === 0 ? 'slideIn 0.3s ease-out' : 'none',
                        opacity: alert.acknowledged ? 0.6 : 1
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                          <span style={{ padding: '4px 12px', borderRadius: '12px', fontSize: '10px', fontWeight: '700', background: severityColor, color: 'white', textTransform: 'uppercase' }}>
                            {alert.severity}
                          </span>
                          <span style={{ padding: '4px 12px', borderRadius: '12px', fontSize: '10px', background: 'rgba(255,255,255,0.1)', color: 'rgba(255,255,255,0.7)' }}>
                            {alert.type}
                          </span>
                          <span style={{ fontSize: '20px' }}>{alert.channelIcon}</span>
                        </div>
                        <div style={{ fontSize: '13px', color: 'rgba(255,255,255,0.9)', marginBottom: '8px', fontWeight: '500' }}>{alert.message}</div>
                        <div style={{ display: 'flex', gap: '20px', fontSize: '11px', color: 'rgba(255,255,255,0.5)' }}>
                          <span>📍 {alert.city || 'Unknown'}</span>
                          <span>🕐 {new Date(alert.timestamp).toLocaleTimeString()}</span>
                          <span>⚠️ Churn: {(alert.churnRisk * 100).toFixed(0)}%</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  );
};

export default EnhancedSentimentDashboard;
