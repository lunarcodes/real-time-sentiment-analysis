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
 * UPDATED: Generates BALANCED data across ALL channels, products, and topics
 * Ensures dashboard shows data for ALL possible combinations
 */
public class TwitterProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(TwitterProducer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private static final String TOPIC = "social-media-raw";
    private static final int MESSAGES_PER_SECOND = 50;
    
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
    
    // ALL CHANNELS: social_media, mobile_app, website, branch, call_center
    // ALL PRODUCTS: accounts, credit_cards, loans, investments, transfers, savings, insurance, mortgages
    
    private static final List<MessageTemplate> POSITIVE_MESSAGES = Arrays.asList(
        // SOCIAL MEDIA CHANNEL - 5 messages
        new MessageTemplate("The new mobile app is amazing! So easy to use!", "social_media", "accounts", Arrays.asList("user_interface", "ease_of_use"), 0.85, 0.95),
        new MessageTemplate("Love my new credit card! Great rewards program!", "social_media", "credit_cards", Arrays.asList("rewards", "satisfaction"), 0.88, 0.93),
        new MessageTemplate("Best loan rates I've found anywhere! #banking", "social_media", "loans", Arrays.asList("pricing", "competitive_rates"), 0.82, 0.90),
        new MessageTemplate("Investment returns exceeded expectations!", "social_media", "investments", Arrays.asList("performance", "returns"), 0.90, 0.94),
        new MessageTemplate("Transfer completed in seconds! Impressive", "social_media", "transfers", Arrays.asList("speed", "efficiency"), 0.86, 0.92),
        
        // MOBILE APP CHANNEL - 5 messages
        new MessageTemplate("Mobile app update is fantastic! Love new features", "mobile_app", "accounts", Arrays.asList("features", "user_experience"), 0.84, 0.91),
        new MessageTemplate("Credit card management on app is so smooth", "mobile_app", "credit_cards", Arrays.asList("app_functionality", "convenience"), 0.87, 0.93),
        new MessageTemplate("Loan application through app was super easy!", "mobile_app", "loans", Arrays.asList("application_process", "digital_experience"), 0.89, 0.94),
        new MessageTemplate("Investment tracking in app is excellent", "mobile_app", "investments", Arrays.asList("portfolio_management", "analytics"), 0.85, 0.92),
        new MessageTemplate("Insurance claim filed through app - seamless!", "mobile_app", "insurance", Arrays.asList("claims", "digital_service"), 0.83, 0.90),
        
        // WEBSITE CHANNEL - 5 messages
        new MessageTemplate("Website redesign is beautiful and functional", "website", "accounts", Arrays.asList("design", "usability"), 0.86, 0.93),
        new MessageTemplate("Online credit card application was quick!", "website", "credit_cards", Arrays.asList("online_application", "speed"), 0.88, 0.94),
        new MessageTemplate("Mortgage calculator on website is very helpful", "website", "mortgages", Arrays.asList("tools", "information"), 0.84, 0.91),
        new MessageTemplate("Savings account interest rates are great!", "website", "savings", Arrays.asList("interest_rates", "competitive"), 0.87, 0.92),
        new MessageTemplate("Website security features give me confidence", "website", "security", Arrays.asList("security", "trust"), 0.90, 0.95),
        
        // BRANCH CHANNEL - 5 messages
        new MessageTemplate("Branch staff were incredibly helpful today!", "branch", "accounts", Arrays.asList("customer_service", "staff_quality"), 0.89, 0.94),
        new MessageTemplate("Got my credit card instantly at branch. Perfect!", "branch", "credit_cards", Arrays.asList("instant_issuance", "convenience"), 0.91, 0.95),
        new MessageTemplate("Loan officer explained everything clearly", "branch", "loans", Arrays.asList("advisory", "communication"), 0.88, 0.93),
        new MessageTemplate("Investment advisor was very knowledgeable", "branch", "investments", Arrays.asList("advisory", "expertise"), 0.90, 0.94),
        new MessageTemplate("Branch manager resolved my issue immediately", "branch", "customer_service", Arrays.asList("problem_resolution", "efficiency"), 0.87, 0.92),
        
        // CALL CENTER CHANNEL - 5 messages
        new MessageTemplate("Call center agent was patient and helpful", "call_center", "accounts", Arrays.asList("support_quality", "patience"), 0.85, 0.91),
        new MessageTemplate("Credit card dispute resolved on first call!", "call_center", "credit_cards", Arrays.asList("dispute_resolution", "efficiency"), 0.92, 0.95),
        new MessageTemplate("Loan inquiry answered thoroughly by phone", "call_center", "loans", Arrays.asList("information", "support"), 0.84, 0.90),
        new MessageTemplate("Transfer issue fixed within minutes on call", "call_center", "transfers", Arrays.asList("problem_resolution", "speed"), 0.88, 0.93),
        new MessageTemplate("Insurance claim support was outstanding", "call_center", "insurance", Arrays.asList("claims_support", "quality"), 0.86, 0.92)
    );
    
    private static final List<MessageTemplate> NEGATIVE_MESSAGES = Arrays.asList(
        // SOCIAL MEDIA CHANNEL - 5 messages
        new MessageTemplate("Account closure process is a nightmare!", "social_media", "accounts", Arrays.asList("account_closure", "complexity"), -0.88, 0.94),
        new MessageTemplate("Credit card declined for no reason! Embarrassing", "social_media", "credit_cards", Arrays.asList("card_decline", "reliability"), -0.85, 0.92),
        new MessageTemplate("Loan approval taking forever! Unacceptable", "social_media", "loans", Arrays.asList("processing_time", "delays"), -0.82, 0.90),
        new MessageTemplate("Lost money on poor investment advice", "social_media", "investments", Arrays.asList("advisory_quality", "losses"), -0.90, 0.95),
        new MessageTemplate("International transfer failed AGAIN!", "social_media", "transfers", Arrays.asList("reliability", "international"), -0.87, 0.93),
        
        // MOBILE APP CHANNEL - 5 messages
        new MessageTemplate("App crashes every time I try to login!", "mobile_app", "accounts", Arrays.asList("stability", "login_issues"), -0.89, 0.94),
        new MessageTemplate("Can't view credit card statement on app", "mobile_app", "credit_cards", Arrays.asList("functionality", "statements"), -0.84, 0.91),
        new MessageTemplate("Loan payment through app keeps failing", "mobile_app", "loans", Arrays.asList("payment_issues", "reliability"), -0.86, 0.92),
        new MessageTemplate("Investment app is so buggy and slow", "mobile_app", "investments", Arrays.asList("performance", "bugs"), -0.83, 0.90),
        new MessageTemplate("Can't complete transfer - app error", "mobile_app", "transfers", Arrays.asList("errors", "functionality"), -0.88, 0.93),
        
        // WEBSITE CHANNEL - 5 messages
        new MessageTemplate("Website is down during business hours!", "website", "accounts", Arrays.asList("downtime", "availability"), -0.91, 0.95),
        new MessageTemplate("Credit card application stuck at 50%", "website", "credit_cards", Arrays.asList("application_issues", "bugs"), -0.85, 0.92),
        new MessageTemplate("Mortgage calculator shows wrong numbers", "website", "mortgages", Arrays.asList("calculator_error", "accuracy"), -0.80, 0.89),
        new MessageTemplate("Can't access savings account details online", "website", "savings", Arrays.asList("access_issues", "functionality"), -0.84, 0.91),
        new MessageTemplate("Website security questions are ridiculous", "website", "security", Arrays.asList("authentication", "usability"), -0.78, 0.88),
        
        // BRANCH CHANNEL - 5 messages
        new MessageTemplate("Waited 90 minutes at branch! Terrible", "branch", "accounts", Arrays.asList("wait_time", "service_quality"), -0.87, 0.93),
        new MessageTemplate("Branch staff were rude and unhelpful", "branch", "credit_cards", Arrays.asList("staff_behavior", "service_quality"), -0.90, 0.94),
        new MessageTemplate("Loan application lost at branch twice!", "branch", "loans", Arrays.asList("process_failure", "inefficiency"), -0.88, 0.93),
        new MessageTemplate("Investment advisor gave terrible advice", "branch", "investments", Arrays.asList("advisory_quality", "expertise"), -0.86, 0.92),
        new MessageTemplate("Branch closes too early for working people", "branch", "operating_hours", Arrays.asList("accessibility", "hours"), -0.75, 0.87),
        
        // CALL CENTER CHANNEL - 5 messages
        new MessageTemplate("Call center disconnected me 3 times!", "call_center", "accounts", Arrays.asList("call_quality", "reliability"), -0.89, 0.94),
        new MessageTemplate("Credit card fraud - no one answers!", "call_center", "credit_cards", Arrays.asList("fraud_support", "responsiveness"), -0.92, 0.96),
        new MessageTemplate("Loan support agent had no idea. Useless", "call_center", "loans", Arrays.asList("knowledge", "support_quality"), -0.85, 0.91),
        new MessageTemplate("Transfer issue unresolved after 5 calls", "call_center", "transfers", Arrays.asList("resolution", "persistence"), -0.88, 0.93),
        new MessageTemplate("Insurance claim rejected without reason", "call_center", "insurance", Arrays.asList("claims_rejection", "communication"), -0.86, 0.92)
    );
    
    private static final List<MessageTemplate> NEUTRAL_MESSAGES = Arrays.asList(
        // SOCIAL MEDIA CHANNEL - 3 messages
        new MessageTemplate("What are the account opening requirements?", "social_media", "accounts", Arrays.asList("information", "requirements"), 0.02, 0.78),
        new MessageTemplate("Anyone know credit card annual fee amount?", "social_media", "credit_cards", Arrays.asList("fees", "information"), 0.00, 0.75),
        new MessageTemplate("Looking for information on personal loans", "social_media", "loans", Arrays.asList("information", "inquiry"), 0.03, 0.77),
        
        // MOBILE APP CHANNEL - 3 messages
        new MessageTemplate("How to enable fingerprint login on app?", "mobile_app", "accounts", Arrays.asList("features", "setup"), 0.05, 0.80),
        new MessageTemplate("Where is credit card limit displayed?", "mobile_app", "credit_cards", Arrays.asList("navigation", "information"), 0.02, 0.76),
        new MessageTemplate("Need help with investment app settings", "mobile_app", "investments", Arrays.asList("configuration", "support"), 0.00, 0.78),
        
        // WEBSITE CHANNEL - 3 messages
        new MessageTemplate("Checking mortgage rates on website", "website", "mortgages", Arrays.asList("rates", "research"), 0.08, 0.72),
        new MessageTemplate("Looking at savings account options online", "website", "savings", Arrays.asList("products", "comparison"), 0.05, 0.75),
        new MessageTemplate("Comparing transfer fees on website", "website", "transfers", Arrays.asList("fees", "comparison"), 0.03, 0.77),
        
        // BRANCH CHANNEL - 2 messages
        new MessageTemplate("What are branch hours this weekend?", "branch", "operating_hours", Arrays.asList("hours", "information"), 0.02, 0.77),
        new MessageTemplate("Do I need appointment for loan inquiry?", "branch", "loans", Arrays.asList("appointments", "process"), 0.00, 0.76),
        
        // CALL CENTER CHANNEL - 2 messages
        new MessageTemplate("What's the call center number for transfers?", "call_center", "transfers", Arrays.asList("contact", "information"), 0.03, 0.78),
        new MessageTemplate("Call center hours for insurance claims?", "call_center", "insurance", Arrays.asList("hours", "contact"), 0.02, 0.77)
    );
    
    private static final List<String> HASHTAGS = Arrays.asList(
        "banking", "fintech", "customerservice", "mobilebanking", "dubai", 
        "uae", "finance", "money", "savings", "investment"
    );
    
    public static void main(String[] args) {
        LOG.info("Starting Twitter Producer with BALANCED DATA GENERATION...");
        LOG.info("Channels: social_media, mobile_app, website, branch, call_center");
        LOG.info("Products: accounts, credit_cards, loans, investments, transfers, savings, insurance, mortgages");
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "10");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "32768");
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            LOG.info("Producer connected to Kafka. Starting balanced data generation...");
            
            long messageCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (true) {
                try {
                    // Generate burst of messages with balanced distribution
                    int burstSize = ThreadLocalRandom.current().nextInt(
                        MESSAGES_PER_SECOND - 10, 
                        MESSAGES_PER_SECOND + 10
                    );
                    
                    for (int i = 0; i < burstSize; i++) {
                        RawTweet tweet = generateBalancedTweet();
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
    
    /**
     * Generate tweet with BALANCED distribution across:
     * - All 5 channels (social_media, mobile_app, website, branch, call_center)
     * - All products (accounts, credit_cards, loans, investments, transfers, savings, insurance, mortgages)
     * - All sentiment types (positive, negative, neutral)
     */
    private static RawTweet generateBalancedTweet() {
        Random rand = ThreadLocalRandom.current();
        
        // BALANCED sentiment distribution: 40% positive, 40% negative, 20% neutral
        double sentimentRoll = rand.nextDouble();
        MessageTemplate template;
        if (sentimentRoll < 0.40) {
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
        String channel;  // social_media, mobile_app, website, branch, call_center
        String product;  // accounts, credit_cards, loans, investments, transfers, savings, insurance, mortgages
        List<String> topics;
        double sentimentScore;
        double confidence;
        
        MessageTemplate(String text, String channel, String product, List<String> topics, double sentimentScore, double confidence) {
            this.text = text;
            this.channel = channel;
            this.product = product;
            this.topics = topics;
            this.sentimentScore = sentimentScore;
            this.confidence = confidence;
        }
    }
}
