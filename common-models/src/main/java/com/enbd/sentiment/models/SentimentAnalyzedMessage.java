package com.enbd.sentiment.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stage 5: Sentiment Analyzed Message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzedMessage {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("analysis_timestamp")
    private Instant analysisTimestamp;
    
    private Sentiment sentiment;
    
    @JsonProperty("business_intelligence")
    private BusinessIntelligence businessIntelligence;
    
    @JsonProperty("content_analysis")
    private ContentAnalysis contentAnalysis;
    
    @JsonProperty("author_context")
    private AuthorContext authorContext;
    
    @JsonProperty("model_metadata")
    private ModelMetadata modelMetadata;
    
    private Timestamps timestamps;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sentiment {
        @JsonProperty("overall_score")
        private double overallScore;
        
        private String classification;
        private double confidence;
        private String intensity;
        
        private Scores scores;
        
        @JsonProperty("aspect_sentiments")
        private Map<String, AspectSentiment> aspectSentiments;
        
        private Map<String, Double> emotions;
        private Toxicity toxicity;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Scores {
            private double positive;
            private double neutral;
            private double negative;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AspectSentiment {
            private double score;
            private String classification;
            private double confidence;
            private List<String> mentions;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Toxicity {
            private double score;
            
            @JsonProperty("is_toxic")
            private boolean isToxic;
            
            @JsonProperty("is_severe_toxic")
            private boolean isSevereToxic;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessIntelligence {
        @JsonProperty("churn_risk_score")
        private double churnRiskScore;
        
        @JsonProperty("churn_risk_level")
        private String churnRiskLevel;
        
        @JsonProperty("complaint_probability")
        private double complaintProbability;
        
        @JsonProperty("escalation_likelihood")
        private double escalationLikelihood;
        
        @JsonProperty("response_priority")
        private String responsePriority;
        
        @JsonProperty("customer_lifetime_value_impact")
        private String customerLifetimeValueImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentAnalysis {
        private String text;
        private String language;
        private List<String> keywords;
        private List<String> categories;
        
        @JsonProperty("products_mentioned")
        private List<String> productsMentioned;
        
        @JsonProperty("issues_identified")
        private List<String> issuesIdentified;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorContext {
        @JsonProperty("author_id")
        private String authorId;
        
        @JsonProperty("historical_sentiment_avg")
        private double historicalSentimentAvg;
        
        @JsonProperty("total_previous_posts")
        private int totalPreviousPosts;
        
        @JsonProperty("sentiment_trend")
        private String sentimentTrend;
        
        @JsonProperty("is_repeat_complainer")
        private boolean isRepeatComplainer;
        
        @JsonProperty("customer_segment")
        private String customerSegment;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelMetadata {
        @JsonProperty("model_name")
        private String modelName;
        
        @JsonProperty("model_version")
        private String modelVersion;
        
        @JsonProperty("inference_time_ms")
        private long inferenceTimeMs;
        
        @JsonProperty("gpu_id")
        private String gpuId;
        
        @JsonProperty("batch_size")
        private int batchSize;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Timestamps {
        @JsonProperty("posted_at")
        private Instant postedAt;
        
        @JsonProperty("enriched_at")
        private Instant enrichedAt;
        
        @JsonProperty("analyzed_at")
        private Instant analyzedAt;
        
        @JsonProperty("ml_latency_ms")
        private long mlLatencyMs;
    }
}
