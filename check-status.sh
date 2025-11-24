#!/bin/bash

echo "╔════════════════════════════════════════════╗"
echo "║     Sentiment Dashboard Status Check       ║"
echo "╚════════════════════════════════════════════╝"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Function to check service
check_service() {
    local name=$1
    local port=$2
    
    if lsof -i :$port > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $name${NC} (port $port)"
        return 0
    else
        echo -e "${RED}❌ $name${NC} (port $port) - NOT RUNNING"
        return 1
    fi
}

# Check all services
echo "Services:"
check_service "Kafka          " 9092
check_service "Redis          " 6379
check_service "Flink JobManager" 8081
check_service "WebSocket      " 8080
check_service "Dashboard      " 3000 || echo -e "${YELLOW}⚠️  Dashboard${NC} (port 3000) - Use HTML dashboard instead"

echo ""
echo "Processes:"
if ps aux | grep -v grep | grep -q TwitterProducer; then
    echo -e "${GREEN}✅ Producer${NC}"
else
    echo -e "${RED}❌ Producer${NC} - NOT RUNNING"
fi

echo ""
echo "Flink Jobs:"
if lsof -i :8081 > /dev/null 2>&1; then
    curl -s http://localhost:8081/jobs 2>/dev/null | jq -r '.jobs[] | "  " + (if .status == "RUNNING" then "✅" else "❌" end) + " " + .name + " (" + .status + ")"' 2>/dev/null || echo "  ⚠️  Could not fetch job status"
else
    echo -e "  ${RED}Flink not running${NC}"
fi

echo ""
echo "Kafka Topics:"
if lsof -i :9092 > /dev/null 2>&1; then
    for topic in social-media-raw enriched-messages sentiment-analyzed aggregated-metrics; do
        if kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -q "$topic"; then
            count=$(kcat -b localhost:9092 -Q -t $topic 2>/dev/null | awk -F':' '{sum+=$NF} END {print sum}')
            printf "  ${GREEN}✅${NC} %-25s: %s messages\n" "$topic" "$count"
        else
            printf "  ${RED}❌${NC} %-25s: NOT FOUND\n" "$topic"
        fi
    done
else
    echo -e "  ${RED}Kafka not running${NC}"
fi

echo ""
echo "Logs:"
echo "  Producer:   tail -f ~/sentiment-dashboard/logs/producer.log"
echo "  WebSocket:  tail -f ~/sentiment-dashboard/logs/websocket.log"
echo "  Flink:      tail -f ~/flink-1.18.0/log/flink-*-taskexecutor-*.log"

echo ""
echo "Dashboards:"
echo "  Flink UI:   http://localhost:8081"
echo "  Dashboard:  file:///$HOME/sentiment-dashboard/dashboard.html"

echo ""
echo "╚════════════════════════════════════════════╝"
