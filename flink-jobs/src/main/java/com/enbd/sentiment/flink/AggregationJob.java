package com.enbd.sentiment.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregation Job - Aggregates sentiment data by time windows
 */
public class AggregationJob {
    
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    public static void main(String[] args) throws Exception {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        
        // Configure Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setTopics("sentiment-analyzed")
            .setGroupId("aggregation-consumer-group")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        DataStream<String> analyzedMessages = env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            "Kafka Source"
        );
        
        // Parse and key by city (or use "Global" if no city)
        // FIX: Added .returns() to specify the type information explicitly
        DataStream<Tuple2<String, String>> keyedMessages = analyzedMessages
            .map(new CityExtractorMapFunction())
            .returns(Types.TUPLE(Types.STRING, Types.STRING));
        
        // Aggregate by 60-second windows
        DataStream<String> aggregatedMetrics = keyedMessages
            .keyBy(tuple -> tuple.f0)  // Key by city
            .window(TumblingProcessingTimeWindows.of(Time.seconds(60)))
            .aggregate(new SentimentAggregator());
        
        // Configure Kafka sink
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("aggregated-metrics")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        aggregatedMetrics.sinkTo(sink);
        
        env.execute("Aggregation Job");
    }
    
    /**
     * MapFunction to extract city from message - using a class instead of lambda
     * to avoid type erasure issues
     */
    public static class CityExtractorMapFunction implements MapFunction<String, Tuple2<String, String>> {
        
        private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
        
        @Override
        public Tuple2<String, String> map(String message) throws Exception {
            try {
                JsonNode json = MAPPER.readTree(message);
                String city = "Global"; // Default
                
                // Try to extract city from the message structure
                // Check content_analysis -> location or other fields
                if (json.has("content_analysis")) {
                    JsonNode contentAnalysis = json.get("content_analysis");
                    if (contentAnalysis.has("location")) {
                        JsonNode location = contentAnalysis.get("location");
                        if (location.has("city")) {
                            city = location.get("city").asText("Global");
                        }
                    }
                }
                
                return new Tuple2<>(city, message);
            } catch (Exception e) {
                return new Tuple2<>("Global", message);
            }
        }
    }
    
    /**
     * Aggregator that collects and computes metrics over a window
     */
    public static class SentimentAggregator implements 
        AggregateFunction<Tuple2<String, String>, MetricsAccumulator, String> {
        
        private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
        
        @Override
        public MetricsAccumulator createAccumulator() {
            return new MetricsAccumulator();
        }
        
        @Override
        public MetricsAccumulator add(Tuple2<String, String> value, MetricsAccumulator accumulator) {
            try {
                String city = value.f0;
                JsonNode message = MAPPER.readTree(value.f1);
                
                accumulator.city = city;
                accumulator.messageCount++;
                
                // Extract sentiment information
                if (message.has("sentiment")) {
                    JsonNode sentiment = message.get("sentiment");
                    double score = sentiment.get("overall_score").asDouble(0.0);
                    String classification = sentiment.get("classification").asText("neutral");
                    
                    accumulator.totalSentimentScore += score;
                    
                    if ("positive".equals(classification)) {
                        accumulator.positiveCount++;
                    } else if ("negative".equals(classification)) {
                        accumulator.negativeCount++;
                    } else {
                        accumulator.neutralCount++;
                    }
                }
                
                // Extract churn risk
                if (message.has("business_intelligence")) {
                    JsonNode bi = message.get("business_intelligence");
                    double churnRisk = bi.get("churn_risk_score").asDouble(0.0);
                    accumulator.totalChurnRisk += churnRisk;
                    
                    if (churnRisk > 0.7) {
                        accumulator.highChurnCount++;
                    } else if (churnRisk > 0.4) {
                        accumulator.mediumChurnCount++;
                    } else {
                        accumulator.lowChurnCount++;
                    }
                }
                
            } catch (Exception e) {
                // Skip malformed messages
            }
            
            return accumulator;
        }
        
        @Override
        public String getResult(MetricsAccumulator accumulator) {
            try {
                ObjectNode result = MAPPER.createObjectNode();
                
                // Aggregation ID
                result.put("aggregation_id", "agg_" + System.currentTimeMillis());
                
                // Window information
                ObjectNode window = result.putObject("window");
                window.put("type", "tumbling");
                window.put("size_seconds", 60);
                Instant now = Instant.now();
                window.put("end_time", now.toString());
                window.put("start_time", now.minusSeconds(60).toString());
                
                // Dimensions
                ObjectNode dimensions = result.putObject("dimensions");
                dimensions.put("city", accumulator.city);
                dimensions.put("country", "Multiple");
                dimensions.put("channel", "social_media");
                
                // Basic metrics
                ObjectNode metrics = result.putObject("metrics");
                metrics.put("total_messages", accumulator.messageCount);
                metrics.put("timeframe_seconds", 60);
                metrics.put("messages_per_second", accumulator.messageCount / 60.0);
                
                // Sentiment distribution
                ObjectNode sentimentDist = result.putObject("sentiment_distribution");
                
                double total = accumulator.messageCount > 0 ? accumulator.messageCount : 1;
                
                ObjectNode positive = sentimentDist.putObject("positive");
                positive.put("count", accumulator.positiveCount);
                positive.put("percentage", (accumulator.positiveCount / total) * 100);
                
                ObjectNode neutral = sentimentDist.putObject("neutral");
                neutral.put("count", accumulator.neutralCount);
                neutral.put("percentage", (accumulator.neutralCount / total) * 100);
                
                ObjectNode negative = sentimentDist.putObject("negative");
                negative.put("count", accumulator.negativeCount);
                negative.put("percentage", (accumulator.negativeCount / total) * 100);
                
                // Sentiment stats
                ObjectNode sentimentStats = result.putObject("sentiment_stats");
                double avgSentiment = accumulator.messageCount > 0 ? 
                    accumulator.totalSentimentScore / accumulator.messageCount : 0.0;
                sentimentStats.put("overall_avg_score", avgSentiment);
                
                String overallClass;
                if (avgSentiment > 0.2) {
                    overallClass = "positive";
                } else if (avgSentiment < -0.2) {
                    overallClass = "negative";
                } else {
                    overallClass = "neutral";
                }
                sentimentStats.put("overall_classification", overallClass);
                
                // Business metrics
                ObjectNode businessMetrics = result.putObject("business_metrics");
                businessMetrics.put("high_churn_risk_count", accumulator.highChurnCount);
                businessMetrics.put("medium_churn_risk_count", accumulator.mediumChurnCount);
                businessMetrics.put("low_churn_risk_count", accumulator.lowChurnCount);
                
                double avgChurnRisk = accumulator.messageCount > 0 ? 
                    accumulator.totalChurnRisk / accumulator.messageCount : 0.0;
                businessMetrics.put("avg_churn_risk_score", avgChurnRisk);
                businessMetrics.put("urgent_response_required", accumulator.highChurnCount);
                
                // Timestamps
                ObjectNode timestamps = result.putObject("timestamps");
                timestamps.put("aggregation_completed_at", now.toString());
                
                return MAPPER.writeValueAsString(result);
                
            } catch (Exception e) {
                return "{}";
            }
        }
        
        @Override
        public MetricsAccumulator merge(MetricsAccumulator a, MetricsAccumulator b) {
            MetricsAccumulator merged = new MetricsAccumulator();
            merged.city = a.city;
            merged.messageCount = a.messageCount + b.messageCount;
            merged.positiveCount = a.positiveCount + b.positiveCount;
            merged.neutralCount = a.neutralCount + b.neutralCount;
            merged.negativeCount = a.negativeCount + b.negativeCount;
            merged.totalSentimentScore = a.totalSentimentScore + b.totalSentimentScore;
            merged.totalChurnRisk = a.totalChurnRisk + b.totalChurnRisk;
            merged.highChurnCount = a.highChurnCount + b.highChurnCount;
            merged.mediumChurnCount = a.mediumChurnCount + b.mediumChurnCount;
            merged.lowChurnCount = a.lowChurnCount + b.lowChurnCount;
            return merged;
        }
    }
    
    /**
     * Accumulator for metrics
     */
    public static class MetricsAccumulator {
        public String city = "Global";
        public int messageCount = 0;
        public int positiveCount = 0;
        public int neutralCount = 0;
        public int negativeCount = 0;
        public double totalSentimentScore = 0.0;
        public double totalChurnRisk = 0.0;
        public int highChurnCount = 0;
        public int mediumChurnCount = 0;
        public int lowChurnCount = 0;
    }
}
