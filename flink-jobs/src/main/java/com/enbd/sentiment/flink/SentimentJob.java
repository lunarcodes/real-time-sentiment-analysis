package com.enbd.sentiment.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Instant;

/**
 * Sentiment Analysis Job - Analyzes sentiment of enriched messages
 */
public class SentimentJob {
    
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    public static void main(String[] args) throws Exception {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        
        // Configure Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setTopics("enriched-messages")
            .setGroupId("sentiment-consumer-group")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        DataStream<String> enrichedMessages = env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            "Kafka Source"
        );
        
        // Add sentiment analysis
        DataStream<String> analyzedMessages = enrichedMessages.map(message -> {
            JsonNode enriched = MAPPER.readTree(message);
            ObjectNode analyzed = MAPPER.createObjectNode();
            
            // Copy event information
            analyzed.put("event_id", enriched.get("event_id").asText());
            analyzed.put("correlation_id", enriched.get("correlation_id").asText());
            analyzed.put("analysis_timestamp", Instant.now().toString());
            
            // Get cleaned text for analysis
            String cleanedText = enriched.get("content").get("cleaned_text").asText().toLowerCase();
            
            // Simple rule-based sentiment analysis
            double sentimentScore = calculateSentiment(cleanedText);
            
            ObjectNode sentiment = analyzed.putObject("sentiment");
            sentiment.put("overall_score", sentimentScore);
            
            // Classification
            String classification;
            if (sentimentScore > 0.2) {
                classification = "positive";
            } else if (sentimentScore < -0.2) {
                classification = "negative";
            } else {
                classification = "neutral";
            }
            sentiment.put("classification", classification);
            sentiment.put("confidence", 0.85);
            sentiment.put("intensity", Math.abs(sentimentScore) > 0.6 ? "strong" : "moderate");
            
            // Emotion detection
            ObjectNode emotions = sentiment.putObject("emotions");
            if (cleanedText.contains("love") || cleanedText.contains("great") || cleanedText.contains("excellent")) {
                emotions.put("joy", 0.8);
                emotions.put("satisfaction", 0.7);
            } else if (cleanedText.contains("hate") || cleanedText.contains("terrible") || cleanedText.contains("worst")) {
                emotions.put("anger", 0.8);
                emotions.put("frustration", 0.9);
            } else if (cleanedText.contains("sad") || cleanedText.contains("disappointed")) {
                emotions.put("sadness", 0.7);
                emotions.put("disappointment", 0.8);
            }
            
            // Business intelligence
            ObjectNode businessIntel = analyzed.putObject("business_intelligence");
            
            // Calculate churn risk
            double churnRisk = calculateChurnRisk(cleanedText, sentimentScore);
            businessIntel.put("churn_risk_score", churnRisk);
            
            String churnLevel;
            if (churnRisk > 0.7) {
                churnLevel = "high";
            } else if (churnRisk > 0.4) {
                churnLevel = "medium";
            } else {
                churnLevel = "low";
            }
            businessIntel.put("churn_risk_level", churnLevel);
            
            // Complaint probability
            double complaintProb = cleanedText.contains("complaint") || 
                                  cleanedText.contains("terrible") || 
                                  cleanedText.contains("unacceptable") ? 0.9 : 0.2;
            businessIntel.put("complaint_probability", complaintProb);
            
            // Response priority
            String priority;
            if (churnRisk > 0.7 || complaintProb > 0.7) {
                priority = "urgent";
            } else if (churnRisk > 0.4) {
                priority = "high";
            } else {
                priority = "normal";
            }
            businessIntel.put("response_priority", priority);
            
            // Content analysis
            ObjectNode contentAnalysis = analyzed.putObject("content_analysis");
            contentAnalysis.put("text", cleanedText);
            contentAnalysis.put("language", "en");
            
            // Identify products mentioned
            if (cleanedText.contains("app") || cleanedText.contains("mobile")) {
                contentAnalysis.putArray("products_mentioned").add("mobile_app");
            }
            if (cleanedText.contains("credit card") || cleanedText.contains("card")) {
                contentAnalysis.putArray("products_mentioned").add("credit_card");
            }
            if (cleanedText.contains("loan") || cleanedText.contains("mortgage")) {
                contentAnalysis.putArray("products_mentioned").add("loans");
            }
            
            // Timestamps
            ObjectNode timestamps = analyzed.putObject("timestamps");
            timestamps.put("posted_at", enriched.get("timestamps").get("posted_at").asText());
            timestamps.put("enriched_at", enriched.get("timestamps").get("enriched_at").asText());
            timestamps.put("analyzed_at", Instant.now().toString());
            
            return MAPPER.writeValueAsString(analyzed);
        });
        
        // Configure Kafka sink
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("sentiment-analyzed")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        analyzedMessages.sinkTo(sink);
        
        env.execute("Sentiment Analysis Job");
    }
    
    /**
     * Simple rule-based sentiment calculation
     */
    private static double calculateSentiment(String text) {
        double score = 0.0;
        
        // Positive words
        if (text.contains("love")) score += 0.8;
        if (text.contains("great")) score += 0.7;
        if (text.contains("excellent")) score += 0.8;
        if (text.contains("amazing")) score += 0.9;
        if (text.contains("fantastic")) score += 0.8;
        if (text.contains("good")) score += 0.5;
        if (text.contains("best")) score += 0.7;
        if (text.contains("happy")) score += 0.6;
        if (text.contains("impressed")) score += 0.7;
        
        // Negative words
        if (text.contains("hate")) score -= 0.9;
        if (text.contains("terrible")) score -= 0.8;
        if (text.contains("worst")) score -= 0.9;
        if (text.contains("awful")) score -= 0.8;
        if (text.contains("bad")) score -= 0.6;
        if (text.contains("poor")) score -= 0.5;
        if (text.contains("disappointing")) score -= 0.7;
        if (text.contains("frustrated")) score -= 0.7;
        if (text.contains("angry")) score -= 0.8;
        if (text.contains("unacceptable")) score -= 0.9;
        if (text.contains("crash")) score -= 0.6;
        if (text.contains("problem")) score -= 0.4;
        if (text.contains("issue")) score -= 0.4;
        if (text.contains("fail")) score -= 0.6;
        
        // Normalize to -1 to +1 range
        return Math.max(-1.0, Math.min(1.0, score));
    }
    
    /**
     * Calculate churn risk based on sentiment and text patterns
     */
    private static double calculateChurnRisk(String text, double sentimentScore) {
        double risk = 0.0;
        
        // Base risk from negative sentiment
        if (sentimentScore < -0.5) {
            risk += 0.5;
        } else if (sentimentScore < 0) {
            risk += 0.2;
        }
        
        // High-risk keywords
        if (text.contains("closing") || text.contains("close my account")) risk += 0.4;
        if (text.contains("switch") || text.contains("switching")) risk += 0.3;
        if (text.contains("competitor")) risk += 0.3;
        if (text.contains("leaving")) risk += 0.4;
        if (text.contains("worst")) risk += 0.2;
        if (text.contains("never again")) risk += 0.3;
        
        return Math.min(1.0, risk);
    }
}
