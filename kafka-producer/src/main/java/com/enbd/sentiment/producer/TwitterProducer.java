package com.enbd.sentiment.producer;

import com.enbd.sentiment.models.RawTweet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Twitter Producer - Simulates real-time social media data stream
 * Generates realistic tweets with varying sentiments and engagement metrics
 */
public class TwitterProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(TwitterProducer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private static final String TOPIC = "social-media-raw";
    private static final int MESSAGES_PER_SECOND = 50;
    private static final int BURST_INTERVAL_SECONDS = 30;
    
    // Sample data for realistic simulation
    private static final List<Location> LOCATIONS = Arrays.asList(
        new Location("Dubai", "United Arab Emirates", "AE", 25.2048, 55.2708, "Asia/Dubai"),
        new Location("Abu Dhabi", "United Arab Emirates", "AE", 24.4539, 54.3773, "Asia/Dubai"),
        new Location("Sharjah", "United Arab Emirates", "AE", 25.3463, 55.4209, "Asia/Dubai"),
        new Location("London", "United Kingdom", "GB", 51.5074, -0.1278, "Europe/London"),
        new Location("New York", "United States", "US", 40.7128, -74.0060, "America/New_York"),
        new Location("Singapore", "Singapore", "SG", 1.3521, 103.8198, "Asia/Singapore"),
        new Location("Mumbai", "India", "IN", 19.0760, 72.8777, "Asia/Kolkata"),
        new Location("Tokyo", "Japan", "JP", 35.6762, 139.6503, "Asia/Tokyo")
    );
    
    private static final List<MessageTemplate> POSITIVE_MESSAGES = Arrays.asList(
        new MessageTemplate("The new mobile app is amazing! So easy to use 🎉", "mobile_app", Arrays.asList("mobile_app"), 0.85, 0.95),
        new MessageTemplate("Great customer service at the branch today! Very professional", "branch", Arrays.asList("customer_service"), 0.80, 0.90),
        new MessageTemplate("Love how fast the account opening process was!", "accounts", Arrays.asList("accounts", "onboarding"), 0.90, 0.92),
        new MessageTemplate("Investment platform is excellent! Great returns 📈", "investments", Arrays.asList("investments", "platform"), 0.88, 0.93),
        new MessageTemplate("Finally a bank that understands customer needs ❤️", "general", Arrays.asList("customer_experience"), 0.85, 0.91),
        new MessageTemplate("Transfer went through instantly! Impressed", "transfers", Arrays.asList("transfers", "mobile_app"), 0.82, 0.89),
        new MessageTemplate("Best banking experience I've ever had!", "general", Arrays.asList("customer_experience"), 0.92, 0.94),
        new MessageTemplate("Credit card rewards program is fantastic!", "credit_cards", Arrays.asList("credit_cards", "rewards"), 0.87, 0.91)
    );
    
    private static final List<MessageTemplate> NEGATIVE_MESSAGES = Arrays.asList(
        new MessageTemplate("Mobile app keeps crashing! This is unacceptable 😡", "mobile_app", Arrays.asList("mobile_app", "technical_issue"), -0.85, 0.94),
        new MessageTemplate("Waited 2 hours at the branch! Terrible service", "branch", Arrays.asList("branch", "waiting_time"), -0.78, 0.90),
        new MessageTemplate("Can't believe they charged me this fee! Ridiculous", "fees", Arrays.asList("fees", "pricing"), -0.80, 0.88),
        new MessageTemplate("Call center keeps disconnecting. So frustrating!", "call_center", Arrays.asList("call_center", "technical_issue"), -0.82, 0.92),
        new MessageTemplate("Website is down AGAIN! When will you fix this?", "website", Arrays.asList("website", "downtime"), -0.88, 0.93),
        new MessageTemplate("Credit card application rejected without explanation 😠", "credit_cards", Arrays.asList("credit_cards", "application"), -0.75, 0.87),
        new MessageTemplate("Transfer took 3 days! Completely unacceptable", "transfers", Arrays.asList("transfers", "delays"), -0.83, 0.91),
        new MessageTemplate("Worst banking experience ever! Closing my account", "general", Arrays.asList("churn", "complaint"), -0.95, 0.96),
        new MessageTemplate("Loan interest rates are way too high!", "loans", Arrays.asList("loans", "pricing"), -0.72, 0.85),
        new MessageTemplate("Customer service is absolutely useless", "customer_service", Arrays.asList("customer_service"), -0.87, 0.90)
    );
    
    private static final List<MessageTemplate> NEUTRAL_MESSAGES = Arrays.asList(
        new MessageTemplate("Anyone know the branch hours for weekend?", "branch", Arrays.asList("branch", "hours"), 0.05, 0.75),
        new MessageTemplate("How do I reset my mobile app password?", "mobile_app", Arrays.asList("mobile_app", "support"), 0.00, 0.80),
        new MessageTemplate("What documents do I need for account opening?", "accounts", Arrays.asList("accounts", "documentation"), 0.02, 0.78),
        new MessageTemplate("Checking my account balance through the app", "mobile_app", Arrays.asList("mobile_app", "balance"), 0.08, 0.72),
        new MessageTemplate("Need to update my address. What's the process?", "general", Arrays.asList("account_management"), 0.00, 0.76),
        new MessageTemplate("Is the international transfer service available?", "transfers", Arrays.asList("transfers", "international"), 0.03, 0.77)
    );
    
    private static final List<String> HASHTAGS = Arrays.asList(
        "banking", "fintech", "customerservice", "mobilebanking", "dubai", 
        "uae", "finance", "money", "savings", "investment"
    );
    
    public static void main(String[] args) {
        LOG.info("Starting Twitter Producer...");
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "10");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "32768");
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            LOG.info("Producer connected to Kafka. Starting data generation...");
            
            long messageCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (true) {
                try {
                    // Generate burst of messages
                    int burstSize = ThreadLocalRandom.current().nextInt(
                        MESSAGES_PER_SECOND - 10, 
                        MESSAGES_PER_SECOND + 10
                    );
                    
                    for (int i = 0; i < burstSize; i++) {
                        RawTweet tweet = generateRealisticTweet();
                        String json = MAPPER.writeValueAsString(tweet);
                        
                        ProducerRecord<String, String> record = new ProducerRecord<>(
                            TOPIC,
                            tweet.getId(),
                            json
                        );
                        
                        producer.send(record, (metadata, exception) -> {
                            if (exception != null) {
                                LOG.error("Error sending message", exception);
                            }
                        });
                        
                        messageCount++;
                    }
                    
                    // Log statistics
                    if (messageCount % 500 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double rate = (messageCount * 1000.0) / elapsed;
                        LOG.info("Sent {} messages | Rate: {:.2f} msg/sec", messageCount, rate);
                    }
                    
                    // Sleep to maintain rate
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    LOG.error("Error in message generation loop", e);
                    Thread.sleep(1000);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Fatal error in producer", e);
            System.exit(1);
        }
    }
    
    private static RawTweet generateRealisticTweet() {
        Random rand = ThreadLocalRandom.current();
        
        // Sentiment distribution: 30% positive, 50% negative, 20% neutral (realistic for customer feedback)
        double sentimentRoll = rand.nextDouble();
        MessageTemplate template;
        if (sentimentRoll < 0.30) {
            template = POSITIVE_MESSAGES.get(rand.nextInt(POSITIVE_MESSAGES.size()));
        } else if (sentimentRoll < 0.80) {
            template = NEGATIVE_MESSAGES.get(rand.nextInt(NEGATIVE_MESSAGES.size()));
        } else {
            template = NEUTRAL_MESSAGES.get(rand.nextInt(NEUTRAL_MESSAGES.size()));
        }
        
        Location location = LOCATIONS.get(rand.nextInt(LOCATIONS.size()));
        
        // Generate author
        String authorId = String.valueOf(1000000000L + rand.nextInt(900000000));
        String username = "user_" + authorId.substring(authorId.length() - 6);
        
        RawTweet.Author author = RawTweet.Author.builder()
            .id(authorId)
            .username(username)
            .name(generateRandomName())
            .location(location.name + ", " + location.country)
            .verified(rand.nextDouble() < 0.05)
            .followersCount(rand.nextInt(10000))
            .createdAt(Instant.now().minusSeconds(rand.nextInt(31536000)))
            .build();
        
        // Generate entities
        List<RawTweet.Entities.Hashtag> hashtags = new ArrayList<>();
        int hashtagCount = rand.nextInt(3);
        for (int i = 0; i < hashtagCount; i++) {
            hashtags.add(RawTweet.Entities.Hashtag.builder()
                .start(50 + i * 10)
                .end(60 + i * 10)
                .tag(HASHTAGS.get(rand.nextInt(HASHTAGS.size())))
                .build());
        }
        
        RawTweet.Entities entities = RawTweet.Entities.builder()
            .hashtags(hashtags)
            .mentions(new ArrayList<>())
            .urls(new ArrayList<>())
            .build();
        
        // Generate engagement metrics
        RawTweet.PublicMetrics metrics = RawTweet.PublicMetrics.builder()
            .retweetCount(rand.nextInt(50))
            .replyCount(rand.nextInt(30))
            .likeCount(rand.nextInt(100))
            .quoteCount(rand.nextInt(10))
            .impressionCount(rand.nextInt(5000) + 100)
            .build();
        
        // Build final tweet
        String tweetId = String.valueOf(System.currentTimeMillis()) + rand.nextInt(1000);
        Instant now = Instant.now();
        
        return RawTweet.builder()
            .id(tweetId)
            .text(template.text)
            .createdAt(now)
            .authorId(author.getId())
            .author(author)
            .entities(entities)
            .publicMetrics(metrics)
            .lang("en")
            .possiblySensitive(false)
            .replySettings("everyone")
            .build();
    }
    
    private static String generateRandomName() {
        String[] firstNames = {"John", "Sarah", "Michael", "Emma", "Ahmed", "Fatima", "David", "Maria", "Mohammed", "Anna"};
        String[] lastNames = {"Smith", "Johnson", "Al-Mansoori", "Garcia", "Khan", "Abdullah", "Brown", "Lee", "Wilson", "Martinez"};
        Random rand = ThreadLocalRandom.current();
        return firstNames[rand.nextInt(firstNames.length)] + " " + lastNames[rand.nextInt(lastNames.length)];
    }
    
    // Helper classes
    private static class Location {
        String name;
        String country;
        String countryCode;
        double latitude;
        double longitude;
        String timezone;
        
        Location(String name, String country, String countryCode, double latitude, double longitude, String timezone) {
            this.name = name;
            this.country = country;
            this.countryCode = countryCode;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
        }
    }
    
    private static class MessageTemplate {
        String text;
        String category;
        List<String> topics;
        double sentimentScore;
        double confidence;
        
        MessageTemplate(String text, String category, List<String> topics, double sentimentScore, double confidence) {
            this.text = text;
            this.category = category;
            this.topics = topics;
            this.sentimentScore = sentimentScore;
            this.confidence = confidence;
        }
    }
}
