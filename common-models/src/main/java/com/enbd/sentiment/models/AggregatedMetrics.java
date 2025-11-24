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
 * Stage 6: Aggregated Metrics from Flink windowing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedMetrics {
    
    @JsonProperty("aggregation_id")
    private String aggregationId;
    
    private Window window;
    private Dimensions dimensions;
    private Metrics metrics;
    
    @JsonProperty("sentiment_distribution")
    private SentimentDistribution sentimentDistribution;
    
    @JsonProperty("sentiment_stats")
    private SentimentStats sentimentStats;
    
    @JsonProperty("top_topics")
    private List<TopicMetric> topTopics;
    
    @JsonProperty("top_products")
    private List<ProductMetric> topProducts;
    
    @JsonProperty("engagement_stats")
    private EngagementStats engagementStats;
    
    @JsonProperty("business_metrics")
    private BusinessMetrics businessMetrics;
    
    private Timestamps timestamps;
    private Metadata metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Window {
        private String type;
        
        @JsonProperty("size_seconds")
        private int sizeSeconds;
        
        @JsonProperty("start_time")
        private Instant startTime;
        
        @JsonProperty("end_time")
        private Instant endTime;
        
        @JsonProperty("current_time")
        private Instant currentTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimensions {
        private String city;
        private String country;
        
        @JsonProperty("country_code")
        private String countryCode;
        
        private String channel;
        private String source;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        @JsonProperty("total_messages")
        private int totalMessages;
        
        @JsonProperty("unique_authors")
        private int uniqueAuthors;
        
        @JsonProperty("timeframe_seconds")
        private int timeframeSeconds;
        
        @JsonProperty("messages_per_second")
        private double messagesPerSecond;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentDistribution {
        private SentimentCategory positive;
        private SentimentCategory neutral;
        private SentimentCategory negative;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SentimentCategory {
            private int count;
            private double percentage;
            
            @JsonProperty("avg_score")
            private double avgScore;
            
            @JsonProperty("avg_confidence")
            private double avgConfidence;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentStats {
        @JsonProperty("overall_avg_score")
        private double overallAvgScore;
        
        @JsonProperty("overall_classification")
        private String overallClassification;
        
        @JsonProperty("median_score")
        private double medianScore;
        
        @JsonProperty("std_deviation")
        private double stdDeviation;
        
        @JsonProperty("min_score")
        private double minScore;
        
        @JsonProperty("max_score")
        private double maxScore;
        
        private Trend trend;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Trend {
            private String direction;
            
            @JsonProperty("change_from_previous_window")
            private double changeFromPreviousWindow;
            
            private double velocity;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicMetric {
        private String topic;
        private int count;
        
        @JsonProperty("avg_sentiment")
        private double avgSentiment;
        
        private boolean trending;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductMetric {
        private String product;
        private int mentions;
        
        @JsonProperty("avg_sentiment")
        private double avgSentiment;
        
        @JsonProperty("churn_risk_avg")
        private double churnRiskAvg;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementStats {
        @JsonProperty("total_impressions")
        private int totalImpressions;
        
        @JsonProperty("total_retweets")
        private int totalRetweets;
        
        @JsonProperty("total_likes")
        private int totalLikes;
        
        @JsonProperty("total_replies")
        private int totalReplies;
        
        @JsonProperty("avg_reach_score")
        private double avgReachScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessMetrics {
        @JsonProperty("high_churn_risk_count")
        private int highChurnRiskCount;
        
        @JsonProperty("medium_churn_risk_count")
        private int mediumChurnRiskCount;
        
        @JsonProperty("low_churn_risk_count")
        private int lowChurnRiskCount;
        
        @JsonProperty("avg_churn_risk_score")
        private double avgChurnRiskScore;
        
        @JsonProperty("urgent_response_required")
        private int urgentResponseRequired;
        
        @JsonProperty("complaint_escalation_predicted")
        private int complaintEscalationPredicted;
        
        @JsonProperty("upsell_opportunities")
        private int upsellOpportunities;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Timestamps {
        @JsonProperty("window_start")
        private Instant windowStart;
        
        @JsonProperty("window_end")
        private Instant windowEnd;
        
        @JsonProperty("aggregation_completed_at")
        private Instant aggregationCompletedAt;
        
        @JsonProperty("processing_time_ms")
        private long processingTimeMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("flink_job_id")
        private String flinkJobId;
        
        private Instant watermark;
        
        @JsonProperty("late_arrivals_count")
        private int lateArrivalsCount;
    }
}
