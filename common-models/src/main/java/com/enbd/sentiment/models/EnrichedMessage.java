package com.enbd.sentiment.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Stage 4: Enriched Message after Flink processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedMessage {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("enrichment_timestamp")
    private Instant enrichmentTimestamp;
    
    private Source source;
    private Content content;
    private Author author;
    private Location location;
    
    @JsonProperty("extracted_features")
    private ExtractedFeatures extractedFeatures;
    
    private Engagement engagement;
    private Timestamps timestamps;
    private Metadata metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String platform;
        
        @JsonProperty("post_id")
        private String postId;
        
        private String channel;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        @JsonProperty("original_text")
        private String originalText;
        
        @JsonProperty("cleaned_text")
        private String cleanedText;
        
        @JsonProperty("normalized_text")
        private String normalizedText;
        
        private String language;
        
        @JsonProperty("char_count")
        private int charCount;
        
        @JsonProperty("word_count")
        private int wordCount;
        
        @JsonProperty("emoji_count")
        private int emojiCount;
        
        @JsonProperty("profanity_detected")
        private boolean profanityDetected;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private String id;
        private String username;
        
        @JsonProperty("display_name")
        private String displayName;
        
        @JsonProperty("follower_count")
        private int followerCount;
        
        @JsonProperty("account_age_days")
        private int accountAgeDays;
        
        private boolean verified;
        
        @JsonProperty("influence_score")
        private double influenceScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String raw;
        private ParsedLocation parsed;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ParsedLocation {
            private String city;
            private String region;
            private String country;
            
            @JsonProperty("country_code")
            private String countryCode;
            
            private Coordinates coordinates;
            private String timezone;
            private double confidence;
            
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Coordinates {
                private double latitude;
                private double longitude;
            }
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedFeatures {
        private List<String> keywords;
        private List<String> topics;
        private Entities entities;
        private String intent;
        
        @JsonProperty("urgency_level")
        private String urgencyLevel;
        
        @JsonProperty("contains_question")
        private boolean containsQuestion;
        
        @JsonProperty("requires_response")
        private boolean requiresResponse;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Entities {
            private List<String> products;
            private List<String> issues;
            private List<String> emotions;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Engagement {
        private int retweets;
        private int likes;
        private int replies;
        private int impressions;
        
        @JsonProperty("reach_score")
        private double reachScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Timestamps {
        @JsonProperty("posted_at")
        private Instant postedAt;
        
        @JsonProperty("captured_at")
        private Instant capturedAt;
        
        @JsonProperty("enriched_at")
        private Instant enrichedAt;
        
        @JsonProperty("processing_latency_ms")
        private long processingLatencyMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("enrichment_version")
        private String enrichmentVersion;
        
        @JsonProperty("flink_job_id")
        private String flinkJobId;
        
        @JsonProperty("pipeline_stage")
        private String pipelineStage;
    }
}
