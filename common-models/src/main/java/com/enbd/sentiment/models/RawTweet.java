package com.enbd.sentiment.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Stage 1: Raw Tweet from Twitter API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawTweet {
    
    private String id;
    private String text;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("author_id")
    private String authorId;
    
    private Author author;
    private Entities entities;
    
    @JsonProperty("public_metrics")
    private PublicMetrics publicMetrics;
    
    private String lang;
    
    @JsonProperty("possibly_sensitive")
    private boolean possiblySensitive;
    
    @JsonProperty("reply_settings")
    private String replySettings;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private String id;
        private String username;
        private String name;
        private String location;
        private boolean verified;
        
        @JsonProperty("followers_count")
        private int followersCount;
        
        @JsonProperty("created_at")
        private Instant createdAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entities {
        private List<Hashtag> hashtags;
        private List<Mention> mentions;
        private List<Url> urls;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Hashtag {
            private int start;
            private int end;
            private String tag;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Mention {
            private int start;
            private int end;
            private String username;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Url {
            private int start;
            private int end;
            private String url;
            
            @JsonProperty("expanded_url")
            private String expandedUrl;
            
            @JsonProperty("display_url")
            private String displayUrl;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicMetrics {
        @JsonProperty("retweet_count")
        private int retweetCount;
        
        @JsonProperty("reply_count")
        private int replyCount;
        
        @JsonProperty("like_count")
        private int likeCount;
        
        @JsonProperty("quote_count")
        private int quoteCount;
        
        @JsonProperty("impression_count")
        private int impressionCount;
    }
}
