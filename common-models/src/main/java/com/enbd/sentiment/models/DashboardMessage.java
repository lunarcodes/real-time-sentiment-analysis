package com.enbd.sentiment.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified message format for dashboard display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMessage {
    
    private String id;
    private String text;
    private String sentiment;
    private double score;
    private double confidence;
    private String source;
    private String channel;
    private String gender;
    
    @JsonProperty("is_urgent")
    private boolean isUrgent;
    
    @JsonProperty("churn_risk")
    private double churnRisk;
    
    private Instant timestamp;
    
    private Author author;
    private Location location;
    
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
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String city;
        private String country;
        
        @JsonProperty("country_code")
        private String countryCode;
        
        private double latitude;
        private double longitude;
    }
}
