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
 * Enrichment Job - Cleans and enriches raw tweets
 */
public class EnrichmentJob {
    
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    public static void main(String[] args) throws Exception {
        
        // Set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        
        // Configure Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setTopics("social-media-raw")
            .setGroupId("enrichment-consumer-group")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        // Read from Kafka
        DataStream<String> rawTweets = env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            "Kafka Source"
        );
        
        // Enrich the data
        DataStream<String> enrichedMessages = rawTweets.map(tweet -> {
            try {
                JsonNode json = MAPPER.readTree(tweet);
                ObjectNode enriched = MAPPER.createObjectNode();
                
                // Generate event ID - with null checks
                String tweetId = json.has("id") ? json.get("id").asText() : String.valueOf(System.currentTimeMillis());
                String eventId = "evt_" + System.currentTimeMillis() + "_" + 
                                tweetId.substring(0, Math.min(8, tweetId.length()));
                
                enriched.put("event_id", eventId);
                enriched.put("correlation_id", tweetId);
                enriched.put("enrichment_timestamp", Instant.now().toString());
                
                // Source information
                ObjectNode sourceInfo = enriched.putObject("source");
                sourceInfo.put("platform", "twitter");
                sourceInfo.put("post_id", tweetId);
                sourceInfo.put("channel", "social_media");
                
                // Content processing
                ObjectNode content = enriched.putObject("content");
                String originalText = json.has("text") ? json.get("text").asText() : "";
            
            // Clean text - remove emojis, URLs, extra spaces
            String cleanedText = originalText
                .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "")  // Remove emojis
                .replaceAll("http\\S+", "")                      // Remove URLs
                .replaceAll("\\s+", " ")                         // Normalize spaces
                .toLowerCase()
                .trim();
            
            content.put("original_text", originalText);
            content.put("cleaned_text", cleanedText);
            content.put("normalized_text", cleanedText);
            content.put("language", json.has("lang") ? json.get("lang").asText("en") : "en");
            content.put("char_count", cleanedText.length());
            content.put("word_count", cleanedText.split("\\s+").length);
            
            // Author information
            ObjectNode author = enriched.putObject("author");
            JsonNode authorNode = json.get("author");
            if (authorNode != null) {
                author.put("id", authorNode.get("id").asText());
                author.put("username", authorNode.get("username").asText());
                author.put("display_name", authorNode.get("name").asText());
                author.put("follower_count", authorNode.get("followers_count").asInt(0));
                author.put("verified", authorNode.get("verified").asBoolean(false));
            }
            
            // Location parsing
            ObjectNode location = enriched.putObject("location");
            if (authorNode != null && authorNode.has("location")) {
                String locationStr = authorNode.get("location").asText();
                location.put("raw", locationStr);
                
                ObjectNode parsed = location.putObject("parsed");
                
                // Simple location parsing
                if (locationStr.contains("Dubai")) {
                    parsed.put("city", "Dubai");
                    parsed.put("country", "United Arab Emirates");
                    parsed.put("country_code", "AE");
                    ObjectNode coords = parsed.putObject("coordinates");
                    coords.put("latitude", 25.2048);
                    coords.put("longitude", 55.2708);
                } else if (locationStr.contains("London")) {
                    parsed.put("city", "London");
                    parsed.put("country", "United Kingdom");
                    parsed.put("country_code", "GB");
                    ObjectNode coords = parsed.putObject("coordinates");
                    coords.put("latitude", 51.5074);
                    coords.put("longitude", -0.1278);
                } else if (locationStr.contains("New York")) {
                    parsed.put("city", "New York");
                    parsed.put("country", "United States");
                    parsed.put("country_code", "US");
                    ObjectNode coords = parsed.putObject("coordinates");
                    coords.put("latitude", 40.7128);
                    coords.put("longitude", -74.0060);
                } else {
                    parsed.put("city", "Unknown");
                    parsed.put("country", "Unknown");
                    parsed.put("country_code", "XX");
                }
            }
            
            // Extract features
            ObjectNode features = enriched.putObject("extracted_features");
            
            // Simple keyword extraction
            String lowerText = cleanedText.toLowerCase();
            if (lowerText.contains("app") || lowerText.contains("mobile")) {
                features.putArray("keywords").add("mobile_app");
            }
            if (lowerText.contains("branch") || lowerText.contains("office")) {
                features.putArray("keywords").add("branch");
            }
            if (lowerText.contains("website") || lowerText.contains("site")) {
                features.putArray("keywords").add("website");
            }
            
            // Determine intent
            if (lowerText.contains("?")) {
                features.put("intent", "question");
            } else if (lowerText.contains("!") || lowerText.contains("terrible") || lowerText.contains("worst")) {
                features.put("intent", "complaint");
            } else {
                features.put("intent", "statement");
            }
            
            // Timestamps
            ObjectNode timestamps = enriched.putObject("timestamps");
            timestamps.put("posted_at", json.has("created_at") ? json.get("created_at").asText() : Instant.now().toString());
            timestamps.put("captured_at", json.has("created_at") ? json.get("created_at").asText() : Instant.now().toString());
            timestamps.put("enriched_at", Instant.now().toString());
            
            return MAPPER.writeValueAsString(enriched);
            
            } catch (Exception e) {
                // Skip malformed messages
                return "{}";
            }
        })
        .filter(msg -> !msg.equals("{}")); // Filter out failed messages
        
        // Configure Kafka sink
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("enriched-messages")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        // Write to Kafka
        enrichedMessages.sinkTo(sink);
        
        // Execute the job
        env.execute("Enrichment Job");
    }
}
