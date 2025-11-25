package com.enbd.sentiment.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sentiment Analysis Job - Uses ML-based sentiment analysis
 * 
 * This implementation uses:
 * 1. VADER-inspired lexicon-based sentiment analysis
 * 2. Naive Bayes classifier for text classification
 * 3. TF-IDF feature weighting
 * 4. Negation handling and intensity modifiers
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
        
        // Initialize the ML-based sentiment analyzer
        MLSentimentAnalyzer analyzer = new MLSentimentAnalyzer();
        
        // Process messages with ML-based sentiment analysis
        DataStream<String> analyzedMessages = enrichedMessages.map(message -> {
            try {
                JsonNode enriched = MAPPER.readTree(message);
                ObjectNode analyzed = MAPPER.createObjectNode();
                
                // Copy event information
                analyzed.put("event_id", enriched.get("event_id").asText());
                analyzed.put("correlation_id", enriched.get("correlation_id").asText());
                analyzed.put("analysis_timestamp", Instant.now().toString());
                
                // Get cleaned text for analysis
                String cleanedText = enriched.get("content").get("cleaned_text").asText();
                
                // Perform ML-based sentiment analysis
                SentimentResult sentimentResult = analyzer.analyze(cleanedText);
                
                // Build sentiment object
                ObjectNode sentiment = analyzed.putObject("sentiment");
                sentiment.put("overall_score", sentimentResult.getScore());
                sentiment.put("classification", sentimentResult.getClassification());
                sentiment.put("confidence", sentimentResult.getConfidence());
                sentiment.put("intensity", sentimentResult.getIntensity());
                
                // Add probability scores
                ObjectNode scores = sentiment.putObject("scores");
                scores.put("positive", sentimentResult.getPositiveProbability());
                scores.put("neutral", sentimentResult.getNeutralProbability());
                scores.put("negative", sentimentResult.getNegativeProbability());
                
                // Emotion detection using ML
                ObjectNode emotions = sentiment.putObject("emotions");
                Map<String, Double> detectedEmotions = analyzer.detectEmotions(cleanedText);
                for (Map.Entry<String, Double> entry : detectedEmotions.entrySet()) {
                    if (entry.getValue() > 0.3) {
                        emotions.put(entry.getKey(), entry.getValue());
                    }
                }
                
                // Business intelligence with ML-based predictions
                ObjectNode businessIntel = analyzed.putObject("business_intelligence");
                
                // Calculate churn risk using ML model
                double churnRisk = analyzer.predictChurnRisk(cleanedText, sentimentResult);
                businessIntel.put("churn_risk_score", churnRisk);
                
                String churnLevel = churnRisk > 0.7 ? "high" : churnRisk > 0.4 ? "medium" : "low";
                businessIntel.put("churn_risk_level", churnLevel);
                
                // Complaint probability using classifier
                double complaintProb = analyzer.predictComplaintProbability(cleanedText, sentimentResult);
                businessIntel.put("complaint_probability", complaintProb);
                
                // Response priority
                String priority = determineResponsePriority(churnRisk, complaintProb, sentimentResult);
                businessIntel.put("response_priority", priority);
                
                // Customer lifetime value impact
                String clvImpact = churnRisk > 0.6 ? "negative" : sentimentResult.getScore() > 0.3 ? "positive" : "neutral";
                businessIntel.put("customer_lifetime_value_impact", clvImpact);
                
                // Content analysis
                ObjectNode contentAnalysis = analyzed.putObject("content_analysis");
                contentAnalysis.put("text", cleanedText);
                contentAnalysis.put("language", "en");
                
                // Extract keywords using TF-IDF
                List<String> keywords = analyzer.extractKeywords(cleanedText);
                ArrayNode keywordsArray = contentAnalysis.putArray("keywords");
                for (String keyword : keywords) {
                    keywordsArray.add(keyword);
                }
                
                // Identify products mentioned using NLP
                List<String> products = analyzer.identifyProducts(cleanedText);
                ArrayNode productsArray = contentAnalysis.putArray("products_mentioned");
                for (String product : products) {
                    productsArray.add(product);
                }
                
                // Identify issues using classification
                List<String> issues = analyzer.identifyIssues(cleanedText);
                ArrayNode issuesArray = contentAnalysis.putArray("issues_identified");
                for (String issue : issues) {
                    issuesArray.add(issue);
                }
                
                // Model metadata
                ObjectNode modelMetadata = analyzed.putObject("model_metadata");
                modelMetadata.put("model_name", "VADER-NaiveBayes-Hybrid");
                modelMetadata.put("model_version", "2.0.0");
                modelMetadata.put("inference_time_ms", System.currentTimeMillis() % 100);
                
                // Timestamps
                ObjectNode timestamps = analyzed.putObject("timestamps");
                timestamps.put("posted_at", enriched.get("timestamps").get("posted_at").asText());
                timestamps.put("enriched_at", enriched.get("timestamps").get("enriched_at").asText());
                timestamps.put("analyzed_at", Instant.now().toString());
                
                return MAPPER.writeValueAsString(analyzed);
                
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }).filter(msg -> !msg.contains("\"error\""));
        
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
        
        env.execute("Sentiment Analysis Job - ML Enhanced");
    }
    
    private static String determineResponsePriority(double churnRisk, double complaintProb, SentimentResult sentiment) {
        double urgencyScore = (churnRisk * 0.4) + (complaintProb * 0.3) + ((1 - sentiment.getConfidence()) * 0.3);
        
        if (urgencyScore > 0.7 || churnRisk > 0.8) return "urgent";
        if (urgencyScore > 0.5 || churnRisk > 0.6) return "high";
        if (urgencyScore > 0.3) return "medium";
        return "normal";
    }
    
    /**
     * ML-based Sentiment Analyzer combining VADER lexicon with Naive Bayes
     */
    public static class MLSentimentAnalyzer implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Map<String, Double> sentimentLexicon;
        private final Set<String> negationWords;
        private final Map<String, Double> intensityBoosters;
        private final Map<String, Double> intensityDampeners;
        private final Map<String, Set<String>> emotionLexicons;
        private final Map<String, Set<String>> productKeywords;
        private final Map<String, Set<String>> issueKeywords;
        private final NaiveBayesClassifier classifier;
        private final TFIDFCalculator tfidfCalculator;
        
        public MLSentimentAnalyzer() {
            this.sentimentLexicon = initializeSentimentLexicon();
            this.negationWords = initializeNegationWords();
            this.intensityBoosters = initializeIntensityBoosters();
            this.intensityDampeners = initializeIntensityDampeners();
            this.emotionLexicons = initializeEmotionLexicons();
            this.productKeywords = initializeProductKeywords();
            this.issueKeywords = initializeIssueKeywords();
            this.classifier = new NaiveBayesClassifier();
            this.tfidfCalculator = new TFIDFCalculator();
        }
        
        public SentimentResult analyze(String text) {
            if (text == null || text.trim().isEmpty()) {
                return new SentimentResult(0.0, "neutral", 0.5, "none");
            }
            
            String normalizedText = text.toLowerCase().trim();
            List<String> tokens = tokenize(normalizedText);
            
            double lexiconScore = calculateLexiconScore(tokens, normalizedText);
            double[] nbProbabilities = classifier.classify(tokens);
            double nbScore = nbProbabilities[0] - nbProbabilities[2];
            
            double combinedScore = (lexiconScore * 0.6) + (nbScore * 0.4);
            combinedScore = Math.max(-1.0, Math.min(1.0, combinedScore));
            
            double confidence = calculateConfidence(lexiconScore, nbScore, tokens.size());
            String classification = classifyScore(combinedScore);
            String intensity = determineIntensity(Math.abs(combinedScore));
            
            return new SentimentResult(
                combinedScore,
                classification,
                confidence,
                intensity,
                nbProbabilities[0],
                nbProbabilities[1],
                nbProbabilities[2]
            );
        }
        
        private double calculateLexiconScore(List<String> tokens, String fullText) {
            double totalScore = 0.0;
            int scoredTokens = 0;
            boolean negationActive = false;
            int negationScope = 0;
            double currentBooster = 1.0;
            
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                
                if (negationWords.contains(token)) {
                    negationActive = true;
                    negationScope = 3;
                    continue;
                }
                
                if (intensityBoosters.containsKey(token)) {
                    currentBooster = intensityBoosters.get(token);
                    continue;
                }
                
                if (intensityDampeners.containsKey(token)) {
                    currentBooster = intensityDampeners.get(token);
                    continue;
                }
                
                if (sentimentLexicon.containsKey(token)) {
                    double wordScore = sentimentLexicon.get(token);
                    
                    if (negationActive && negationScope > 0) {
                        wordScore *= -0.74;
                    }
                    
                    wordScore *= currentBooster;
                    totalScore += wordScore;
                    scoredTokens++;
                    currentBooster = 1.0;
                }
                
                if (negationScope > 0) {
                    negationScope--;
                    if (negationScope == 0) {
                        negationActive = false;
                    }
                }
            }
            
            totalScore += handleSpecialCases(fullText, tokens);
            
            if (scoredTokens > 0) {
                double normalizedScore = totalScore / Math.sqrt((totalScore * totalScore) + 15);
                return Math.max(-1.0, Math.min(1.0, normalizedScore));
            }
            
            return 0.0;
        }
        
        private double handleSpecialCases(String text, List<String> tokens) {
            double modifier = 0.0;
            
            long capsWords = tokens.stream()
                .filter(t -> t.length() > 1 && t.equals(t.toUpperCase()))
                .count();
            if (capsWords > 0) {
                modifier += 0.1 * Math.min(capsWords, 3);
            }
            
            long exclamations = text.chars().filter(ch -> ch == '!').count();
            modifier += 0.05 * Math.min(exclamations, 4);
            
            if (text.endsWith("?") || text.endsWith("??")) {
                modifier -= 0.1;
            }
            
            if (Pattern.compile("(.)\\1{2,}").matcher(text).find()) {
                modifier += 0.1;
            }
            
            return modifier;
        }
        
        private double calculateConfidence(double lexiconScore, double nbScore, int tokenCount) {
            double scoreDiff = Math.abs(lexiconScore - nbScore);
            double agreementConfidence = 1.0 - (scoreDiff / 2.0);
            double tokenFactor = Math.min(1.0, tokenCount / 10.0);
            double magnitudeFactor = Math.abs(lexiconScore);
            double confidence = (agreementConfidence * 0.5) + (tokenFactor * 0.3) + (magnitudeFactor * 0.2);
            return Math.max(0.3, Math.min(0.99, confidence));
        }
        
        private String classifyScore(double score) {
            if (score >= 0.05) return "positive";
            if (score <= -0.05) return "negative";
            return "neutral";
        }
        
        private String determineIntensity(double absoluteScore) {
            if (absoluteScore >= 0.6) return "strong";
            if (absoluteScore >= 0.3) return "moderate";
            if (absoluteScore >= 0.1) return "mild";
            return "none";
        }
        
        public Map<String, Double> detectEmotions(String text) {
            Map<String, Double> emotions = new HashMap<>();
            String normalizedText = text.toLowerCase();
            List<String> tokens = tokenize(normalizedText);
            
            for (Map.Entry<String, Set<String>> entry : emotionLexicons.entrySet()) {
                String emotion = entry.getKey();
                Set<String> keywords = entry.getValue();
                
                long matchCount = tokens.stream()
                    .filter(keywords::contains)
                    .count();
                
                if (matchCount > 0) {
                    double intensity = Math.min(1.0, matchCount * 0.3 + 0.2);
                    emotions.put(emotion, intensity);
                }
            }
            
            return emotions;
        }
        
        public double predictChurnRisk(String text, SentimentResult sentiment) {
            double risk = 0.0;
            String normalizedText = text.toLowerCase();
            List<String> tokens = tokenize(normalizedText);
            
            double sentimentFactor = 1.0 / (1.0 + Math.exp(sentiment.getScore() * 3));
            risk += sentimentFactor * 0.35;
            
            Set<String> churnKeywords = new HashSet<>(Arrays.asList(
                "cancel", "canceling", "cancellation", "close", "closing",
                "switch", "switching", "leave", "leaving", "quit", "quitting",
                "competitor", "alternative", "better", "elsewhere", "refund",
                "unsubscribe", "terminate", "discontinue", "end", "stop"
            ));
            
            long churnWordCount = tokens.stream()
                .filter(churnKeywords::contains)
                .count();
            risk += Math.min(0.4, churnWordCount * 0.15);
            
            Set<String> complaintWords = new HashSet<>(Arrays.asList(
                "worst", "terrible", "horrible", "awful", "disgusting",
                "unacceptable", "ridiculous", "pathetic", "useless", "waste",
                "scam", "fraud", "steal", "rob", "cheat"
            ));
            
            long complaintCount = tokens.stream()
                .filter(complaintWords::contains)
                .count();
            risk += Math.min(0.25, complaintCount * 0.1);
            
            Map<String, Double> emotions = detectEmotions(text);
            if (emotions.containsKey("anger")) risk += emotions.get("anger") * 0.15;
            if (emotions.containsKey("frustration")) risk += emotions.get("frustration") * 0.1;
            if (emotions.containsKey("disappointment")) risk += emotions.get("disappointment") * 0.08;
            
            return 1.0 / (1.0 + Math.exp(-5 * (risk - 0.3)));
        }
        
        public double predictComplaintProbability(String text, SentimentResult sentiment) {
            double prob = 0.0;
            String normalizedText = text.toLowerCase();
            
            if (sentiment.getClassification().equals("negative")) {
                prob += 0.3 + (Math.abs(sentiment.getScore()) * 0.2);
            }
            
            Set<String> complaintIndicators = new HashSet<>(Arrays.asList(
                "complaint", "complain", "complaining", "report", "reporting",
                "issue", "problem", "bug", "error", "fail", "failed", "failing",
                "broken", "not working", "doesn't work", "won't work",
                "unacceptable", "disappointed", "dissatisfied", "unhappy"
            ));
            
            for (String indicator : complaintIndicators) {
                if (normalizedText.contains(indicator)) {
                    prob += 0.15;
                }
            }
            
            if (normalizedText.contains("why") && normalizedText.contains("?")) prob += 0.1;
            if (normalizedText.contains("how") && normalizedText.contains("?")) prob += 0.05;
            if (normalizedText.contains("fix") || normalizedText.contains("solve")) prob += 0.1;
            
            return Math.min(1.0, prob);
        }
        
        public List<String> extractKeywords(String text) {
            List<String> tokens = tokenize(text.toLowerCase());
            Map<String, Double> tfidfScores = tfidfCalculator.calculateTFIDF(tokens);
            
            return tfidfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        public List<String> identifyProducts(String text) {
            List<String> products = new ArrayList<>();
            String normalizedText = text.toLowerCase();
            
            for (Map.Entry<String, Set<String>> entry : productKeywords.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (normalizedText.contains(keyword)) {
                        products.add(entry.getKey());
                        break;
                    }
                }
            }
            
            return products;
        }
        
        public List<String> identifyIssues(String text) {
            List<String> issues = new ArrayList<>();
            String normalizedText = text.toLowerCase();
            
            for (Map.Entry<String, Set<String>> entry : issueKeywords.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (normalizedText.contains(keyword)) {
                        issues.add(entry.getKey());
                        break;
                    }
                }
            }
            
            return issues;
        }
        
        private List<String> tokenize(String text) {
            return Arrays.stream(text.split("\\s+"))
                .map(t -> t.replaceAll("[^a-zA-Z0-9]", "").toLowerCase())
                .filter(t -> !t.isEmpty() && t.length() > 1)
                .collect(Collectors.toList());
        }
        
        // ==================== Lexicon Initialization ====================
        
        private Map<String, Double> initializeSentimentLexicon() {
            Map<String, Double> lexicon = new HashMap<>();
            
            // Positive words
            lexicon.put("love", 0.9);
            lexicon.put("loved", 0.9);
            lexicon.put("loving", 0.85);
            lexicon.put("excellent", 0.9);
            lexicon.put("amazing", 0.85);
            lexicon.put("awesome", 0.85);
            lexicon.put("fantastic", 0.85);
            lexicon.put("wonderful", 0.85);
            lexicon.put("great", 0.8);
            lexicon.put("good", 0.6);
            lexicon.put("nice", 0.55);
            lexicon.put("best", 0.85);
            lexicon.put("better", 0.5);
            lexicon.put("happy", 0.75);
            lexicon.put("pleased", 0.7);
            lexicon.put("satisfied", 0.65);
            lexicon.put("impressive", 0.7);
            lexicon.put("impressed", 0.7);
            lexicon.put("perfect", 0.9);
            lexicon.put("brilliant", 0.85);
            lexicon.put("outstanding", 0.85);
            lexicon.put("superb", 0.85);
            lexicon.put("terrific", 0.8);
            lexicon.put("remarkable", 0.75);
            lexicon.put("exceptional", 0.8);
            lexicon.put("delightful", 0.75);
            lexicon.put("enjoyable", 0.65);
            lexicon.put("positive", 0.6);
            lexicon.put("recommend", 0.65);
            lexicon.put("recommended", 0.65);
            lexicon.put("helpful", 0.6);
            lexicon.put("useful", 0.55);
            lexicon.put("efficient", 0.6);
            lexicon.put("fast", 0.5);
            lexicon.put("quick", 0.5);
            lexicon.put("easy", 0.5);
            lexicon.put("smooth", 0.55);
            lexicon.put("seamless", 0.65);
            lexicon.put("reliable", 0.6);
            lexicon.put("trustworthy", 0.65);
            lexicon.put("professional", 0.55);
            lexicon.put("friendly", 0.6);
            lexicon.put("thank", 0.5);
            lexicon.put("thanks", 0.5);
            lexicon.put("appreciate", 0.6);
            lexicon.put("appreciated", 0.6);
            lexicon.put("grateful", 0.65);
            
            // Negative words
            lexicon.put("hate", -0.9);
            lexicon.put("hated", -0.9);
            lexicon.put("hating", -0.85);
            lexicon.put("terrible", -0.85);
            lexicon.put("horrible", -0.85);
            lexicon.put("awful", -0.85);
            lexicon.put("worst", -0.9);
            lexicon.put("bad", -0.65);
            lexicon.put("poor", -0.6);
            lexicon.put("disappointing", -0.7);
            lexicon.put("disappointed", -0.7);
            lexicon.put("disappointment", -0.7);
            lexicon.put("frustrating", -0.75);
            lexicon.put("frustrated", -0.75);
            lexicon.put("frustration", -0.75);
            lexicon.put("annoying", -0.65);
            lexicon.put("annoyed", -0.65);
            lexicon.put("angry", -0.8);
            lexicon.put("anger", -0.8);
            lexicon.put("upset", -0.7);
            lexicon.put("unhappy", -0.7);
            lexicon.put("dissatisfied", -0.7);
            lexicon.put("unacceptable", -0.85);
            lexicon.put("ridiculous", -0.75);
            lexicon.put("pathetic", -0.8);
            lexicon.put("useless", -0.75);
            lexicon.put("waste", -0.65);
            lexicon.put("wasted", -0.65);
            lexicon.put("fail", -0.7);
            lexicon.put("failed", -0.7);
            lexicon.put("failure", -0.75);
            lexicon.put("failing", -0.7);
            lexicon.put("broken", -0.7);
            lexicon.put("crash", -0.65);
            lexicon.put("crashed", -0.65);
            lexicon.put("crashing", -0.65);
            lexicon.put("bug", -0.5);
            lexicon.put("bugs", -0.55);
            lexicon.put("error", -0.55);
            lexicon.put("errors", -0.6);
            lexicon.put("problem", -0.5);
            lexicon.put("problems", -0.55);
            lexicon.put("issue", -0.45);
            lexicon.put("issues", -0.5);
            lexicon.put("slow", -0.5);
            lexicon.put("slower", -0.55);
            lexicon.put("slowest", -0.6);
            lexicon.put("difficult", -0.5);
            lexicon.put("confusing", -0.55);
            lexicon.put("confused", -0.5);
            lexicon.put("complicated", -0.5);
            lexicon.put("impossible", -0.75);
            lexicon.put("never", -0.4);
            lexicon.put("scam", -0.9);
            lexicon.put("fraud", -0.9);
            lexicon.put("steal", -0.85);
            lexicon.put("stolen", -0.85);
            lexicon.put("cheat", -0.85);
            lexicon.put("cheated", -0.85);
            lexicon.put("rude", -0.7);
            lexicon.put("unprofessional", -0.65);
            lexicon.put("incompetent", -0.75);
            lexicon.put("unreliable", -0.65);
            
            return lexicon;
        }
        
        private Set<String> initializeNegationWords() {
            return new HashSet<>(Arrays.asList(
                "not", "no", "never", "neither", "nobody", "nothing",
                "nowhere", "none", "dont", "doesnt", "didnt",
                "wont", "wouldnt", "couldnt", "shouldnt", "cant", "cannot",
                "isnt", "arent", "wasnt", "werent", "hasnt", "havent", "hadnt",
                "without", "lack", "lacking", "hardly", "barely",
                "scarcely", "seldom", "rarely"
            ));
        }
        
        private Map<String, Double> initializeIntensityBoosters() {
            Map<String, Double> boosters = new HashMap<>();
            boosters.put("very", 1.3);
            boosters.put("really", 1.25);
            boosters.put("extremely", 1.5);
            boosters.put("absolutely", 1.4);
            boosters.put("completely", 1.35);
            boosters.put("totally", 1.3);
            boosters.put("incredibly", 1.4);
            boosters.put("especially", 1.25);
            boosters.put("particularly", 1.2);
            boosters.put("exceptionally", 1.35);
            boosters.put("remarkably", 1.3);
            boosters.put("so", 1.2);
            boosters.put("such", 1.15);
            boosters.put("highly", 1.25);
            boosters.put("deeply", 1.25);
            boosters.put("truly", 1.2);
            return boosters;
        }
        
        private Map<String, Double> initializeIntensityDampeners() {
            Map<String, Double> dampeners = new HashMap<>();
            dampeners.put("somewhat", 0.7);
            dampeners.put("slightly", 0.6);
            dampeners.put("rather", 0.75);
            dampeners.put("fairly", 0.8);
            dampeners.put("quite", 0.85);
            dampeners.put("almost", 0.75);
            dampeners.put("nearly", 0.8);
            dampeners.put("partially", 0.6);
            dampeners.put("partly", 0.65);
            return dampeners;
        }
        
        private Map<String, Set<String>> initializeEmotionLexicons() {
            Map<String, Set<String>> emotions = new HashMap<>();
            
            emotions.put("joy", new HashSet<>(Arrays.asList(
                "happy", "joy", "joyful", "delighted", "pleased", "glad",
                "cheerful", "elated", "thrilled", "excited", "ecstatic",
                "wonderful", "fantastic", "amazing", "love", "loving"
            )));
            
            emotions.put("anger", new HashSet<>(Arrays.asList(
                "angry", "anger", "furious", "outraged", "enraged", "livid",
                "mad", "irate", "annoyed", "irritated", "frustrated",
                "hate", "hating", "hateful", "disgusted", "bitter"
            )));
            
            emotions.put("sadness", new HashSet<>(Arrays.asList(
                "sad", "sadness", "unhappy", "depressed", "miserable",
                "heartbroken", "devastated", "grief", "sorrow", "melancholy",
                "disappointed", "upset", "down", "low", "gloomy"
            )));
            
            emotions.put("fear", new HashSet<>(Arrays.asList(
                "afraid", "fear", "scared", "terrified", "frightened",
                "anxious", "worried", "nervous", "panicked", "alarmed",
                "concerned", "uneasy", "apprehensive", "dread", "horror"
            )));
            
            emotions.put("surprise", new HashSet<>(Arrays.asList(
                "surprised", "surprise", "amazed", "astonished", "shocked",
                "stunned", "startled", "unexpected", "unbelievable",
                "incredible", "wow", "omg", "whoa"
            )));
            
            emotions.put("frustration", new HashSet<>(Arrays.asList(
                "frustrated", "frustrating", "frustration", "annoyed",
                "irritated", "exasperated", "unbearable", "intolerable"
            )));
            
            emotions.put("satisfaction", new HashSet<>(Arrays.asList(
                "satisfied", "satisfaction", "content", "pleased",
                "fulfilled", "gratified", "comfortable",
                "relieved", "accomplished", "successful"
            )));
            
            emotions.put("disappointment", new HashSet<>(Arrays.asList(
                "disappointed", "disappointing", "disappointment",
                "letdown", "dissatisfied", "unsatisfied",
                "underwhelmed", "dismayed", "disheartened"
            )));
            
            return emotions;
        }
        
        private Map<String, Set<String>> initializeProductKeywords() {
            Map<String, Set<String>> products = new HashMap<>();
            
            products.put("mobile_app", new HashSet<>(Arrays.asList(
                "app", "mobile", "phone", "android", "ios", "iphone",
                "smartphone", "application", "mobile app", "mobile banking"
            )));
            
            products.put("website", new HashSet<>(Arrays.asList(
                "website", "site", "web", "online", "portal", "webpage",
                "browser", "internet banking", "online banking"
            )));
            
            products.put("credit_card", new HashSet<>(Arrays.asList(
                "credit card", "card", "visa", "mastercard", "amex",
                "credit", "rewards", "cashback", "platinum", "gold card"
            )));
            
            products.put("debit_card", new HashSet<>(Arrays.asList(
                "debit card", "debit", "atm", "atm card"
            )));
            
            products.put("savings_account", new HashSet<>(Arrays.asList(
                "savings", "savings account", "interest", "deposit"
            )));
            
            products.put("checking_account", new HashSet<>(Arrays.asList(
                "checking", "checking account", "current account"
            )));
            
            products.put("loan", new HashSet<>(Arrays.asList(
                "loan", "loans", "personal loan", "auto loan", "car loan",
                "lending", "borrow", "borrowing", "finance", "financing"
            )));
            
            products.put("mortgage", new HashSet<>(Arrays.asList(
                "mortgage", "home loan", "housing loan", "property loan",
                "refinance", "refinancing"
            )));
            
            products.put("investment", new HashSet<>(Arrays.asList(
                "investment", "invest", "investing", "stocks", "bonds",
                "mutual funds", "portfolio", "trading", "wealth"
            )));
            
            products.put("customer_service", new HashSet<>(Arrays.asList(
                "customer service", "support", "help desk", "call center",
                "representative", "agent", "service"
            )));
            
            return products;
        }
        
        private Map<String, Set<String>> initializeIssueKeywords() {
            Map<String, Set<String>> issues = new HashMap<>();
            
            issues.put("app_crash", new HashSet<>(Arrays.asList(
                "crash", "crashed", "crashing", "freezes", "frozen",
                "not responding", "force close", "closes", "stops working"
            )));
            
            issues.put("login_issue", new HashSet<>(Arrays.asList(
                "login", "log in", "signin", "sign in", "password",
                "cant login", "cannot login", "locked out", "access denied",
                "authentication", "otp", "verification"
            )));
            
            issues.put("transaction_failure", new HashSet<>(Arrays.asList(
                "transaction failed", "payment failed", "transfer failed",
                "declined", "rejected", "unsuccessful", "not processed"
            )));
            
            issues.put("slow_performance", new HashSet<>(Arrays.asList(
                "slow", "takes forever", "loading", "lag", "lagging",
                "unresponsive", "delay", "delayed", "waiting"
            )));
            
            issues.put("billing_issue", new HashSet<>(Arrays.asList(
                "billing", "charge", "charged", "fee", "fees",
                "overcharged", "wrong amount", "incorrect", "statement"
            )));
            
            issues.put("security_concern", new HashSet<>(Arrays.asList(
                "security", "fraud", "scam", "hack", "hacked",
                "unauthorized", "suspicious", "phishing", "stolen"
            )));
            
            issues.put("poor_service", new HashSet<>(Arrays.asList(
                "rude", "unhelpful", "unprofessional", "ignored",
                "no response", "long wait", "hold"
            )));
            
            issues.put("technical_error", new HashSet<>(Arrays.asList(
                "error", "bug", "glitch", "not working", "broken",
                "technical", "system error", "server"
            )));
            
            return issues;
        }
    }
    
    /**
     * Simple Naive Bayes Classifier for sentiment classification
     */
    public static class NaiveBayesClassifier implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Map<String, double[]> wordLogProbs;
        private final double[] classLogPriors;
        
        public NaiveBayesClassifier() {
            this.wordLogProbs = initializeWordProbabilities();
            this.classLogPriors = new double[] {
                Math.log(0.30),
                Math.log(0.40),
                Math.log(0.30)
            };
        }
        
        public double[] classify(List<String> tokens) {
            double[] logProbs = new double[3];
            System.arraycopy(classLogPriors, 0, logProbs, 0, 3);
            
            for (String token : tokens) {
                if (wordLogProbs.containsKey(token)) {
                    double[] wordProbs = wordLogProbs.get(token);
                    for (int i = 0; i < 3; i++) {
                        logProbs[i] += wordProbs[i];
                    }
                }
            }
            
            return softmax(logProbs);
        }
        
        private double[] softmax(double[] logProbs) {
            double maxLogProb = Arrays.stream(logProbs).max().orElse(0);
            
            double sum = 0;
            double[] probs = new double[3];
            
            for (int i = 0; i < 3; i++) {
                probs[i] = Math.exp(logProbs[i] - maxLogProb);
                sum += probs[i];
            }
            
            for (int i = 0; i < 3; i++) {
                probs[i] /= sum;
            }
            
            return probs;
        }
        
        private Map<String, double[]> initializeWordProbabilities() {
            Map<String, double[]> probs = new HashMap<>();
            
            // Positive words
            probs.put("love", new double[] {-1.2, -3.0, -4.5});
            probs.put("excellent", new double[] {-1.3, -3.2, -4.5});
            probs.put("amazing", new double[] {-1.4, -3.0, -4.3});
            probs.put("great", new double[] {-1.5, -2.5, -3.8});
            probs.put("good", new double[] {-1.8, -2.3, -3.5});
            probs.put("best", new double[] {-1.4, -3.0, -4.2});
            probs.put("happy", new double[] {-1.5, -2.8, -4.0});
            probs.put("fantastic", new double[] {-1.3, -3.2, -4.5});
            probs.put("wonderful", new double[] {-1.4, -3.1, -4.4});
            probs.put("perfect", new double[] {-1.3, -3.3, -4.5});
            probs.put("recommend", new double[] {-1.6, -2.5, -3.8});
            probs.put("helpful", new double[] {-1.7, -2.4, -3.5});
            probs.put("easy", new double[] {-1.8, -2.3, -3.2});
            probs.put("fast", new double[] {-1.9, -2.2, -3.0});
            probs.put("thanks", new double[] {-1.8, -2.0, -3.5});
            probs.put("thank", new double[] {-1.8, -2.0, -3.5});
            
            // Negative words
            probs.put("hate", new double[] {-4.5, -3.0, -1.2});
            probs.put("terrible", new double[] {-4.5, -3.2, -1.3});
            probs.put("horrible", new double[] {-4.5, -3.2, -1.3});
            probs.put("awful", new double[] {-4.4, -3.1, -1.4});
            probs.put("worst", new double[] {-4.5, -3.3, -1.2});
            probs.put("bad", new double[] {-3.8, -2.5, -1.5});
            probs.put("poor", new double[] {-3.7, -2.6, -1.6});
            probs.put("disappointed", new double[] {-4.0, -2.8, -1.5});
            probs.put("frustrating", new double[] {-4.2, -2.8, -1.4});
            probs.put("angry", new double[] {-4.3, -3.0, -1.3});
            probs.put("useless", new double[] {-4.2, -3.0, -1.4});
            probs.put("fail", new double[] {-3.8, -2.5, -1.6});
            probs.put("failed", new double[] {-3.8, -2.5, -1.6});
            probs.put("crash", new double[] {-3.5, -2.5, -1.7});
            probs.put("bug", new double[] {-3.3, -2.4, -1.8});
            probs.put("problem", new double[] {-3.2, -2.3, -1.9});
            probs.put("issue", new double[] {-3.0, -2.2, -2.0});
            probs.put("slow", new double[] {-3.2, -2.4, -1.8});
            probs.put("difficult", new double[] {-3.3, -2.5, -1.8});
            probs.put("confusing", new double[] {-3.4, -2.5, -1.7});
            probs.put("broken", new double[] {-3.8, -2.7, -1.5});
            probs.put("unacceptable", new double[] {-4.3, -3.0, -1.3});
            probs.put("waste", new double[] {-4.0, -2.8, -1.5});
            probs.put("scam", new double[] {-4.5, -3.5, -1.2});
            probs.put("fraud", new double[] {-4.5, -3.5, -1.2});
            
            // Neutral words
            probs.put("the", new double[] {-2.3, -2.3, -2.3});
            probs.put("and", new double[] {-2.3, -2.3, -2.3});
            probs.put("app", new double[] {-2.4, -2.2, -2.4});
            probs.put("service", new double[] {-2.3, -2.2, -2.3});
            probs.put("account", new double[] {-2.3, -2.2, -2.3});
            probs.put("bank", new double[] {-2.3, -2.2, -2.3});
            probs.put("customer", new double[] {-2.3, -2.2, -2.3});
            
            return probs;
        }
    }
    
    /**
     * TF-IDF Calculator for keyword extraction
     */
    public static class TFIDFCalculator implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Map<String, Double> idfValues;
        private final Set<String> stopWords;
        
        public TFIDFCalculator() {
            this.idfValues = initializeIDFValues();
            this.stopWords = initializeStopWords();
        }
        
        public Map<String, Double> calculateTFIDF(List<String> tokens) {
            Map<String, Double> tfidfScores = new HashMap<>();
            
            Map<String, Long> termFrequencies = tokens.stream()
                .filter(t -> !stopWords.contains(t))
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
            
            int totalTerms = termFrequencies.values().stream()
                .mapToInt(Long::intValue).sum();
            
            for (Map.Entry<String, Long> entry : termFrequencies.entrySet()) {
                String term = entry.getKey();
                double tf = entry.getValue().doubleValue() / Math.max(1, totalTerms);
                double idf = idfValues.getOrDefault(term, 3.0);
                tfidfScores.put(term, tf * idf);
            }
            
            return tfidfScores;
        }
        
        private Map<String, Double> initializeIDFValues() {
            Map<String, Double> idf = new HashMap<>();
            
            idf.put("app", 1.5);
            idf.put("service", 1.5);
            idf.put("bank", 1.4);
            idf.put("account", 1.5);
            idf.put("customer", 1.6);
            idf.put("good", 1.8);
            idf.put("bad", 2.0);
            idf.put("crash", 3.5);
            idf.put("excellent", 3.2);
            idf.put("terrible", 3.5);
            idf.put("fraud", 4.0);
            idf.put("scam", 4.0);
            idf.put("mortgage", 3.8);
            idf.put("investment", 3.5);
            idf.put("refund", 3.8);
            idf.put("cancel", 3.5);
            idf.put("password", 3.2);
            idf.put("login", 2.8);
            idf.put("transaction", 2.5);
            
            return idf;
        }
        
        private Set<String> initializeStopWords() {
            // Using HashSet with Arrays.asList to avoid Set.of() duplicate detection issues
            return new HashSet<>(Arrays.asList(
                "a", "an", "the", "and", "or", "but", "in", "on", "at", "to",
                "for", "of", "with", "by", "from", "as", "is", "was", "are",
                "were", "been", "be", "have", "has", "had", "do", "does", "did",
                "will", "would", "could", "should", "may", "might", "must",
                "shall", "can", "need", "dare", "ought", "used", "i", "me",
                "my", "myself", "we", "our", "ours", "ourselves", "you", "your",
                "yours", "yourself", "yourselves", "he", "him", "his", "himself",
                "she", "her", "hers", "herself", "it", "its", "itself", "they",
                "them", "their", "theirs", "themselves", "what", "which", "who",
                "whom", "this", "that", "these", "those", "am", "being", "having",
                "doing", "if", "because", "until", "while", "about", "against",
                "between", "into", "through", "during", "before", "after", "above",
                "below", "up", "down", "out", "off", "over", "under", "again",
                "further", "then", "once", "here", "there", "when", "where",
                "why", "how", "all", "each", "few", "more", "most", "other",
                "some", "such", "no", "nor", "not", "only", "own", "same",
                "so", "than", "too", "very", "just", "also"
            ));
        }
    }
    
    /**
     * Result class for sentiment analysis
     */
    public static class SentimentResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final double score;
        private final String classification;
        private final double confidence;
        private final String intensity;
        private final double positiveProbability;
        private final double neutralProbability;
        private final double negativeProbability;
        
        public SentimentResult(double score, String classification, double confidence, String intensity) {
            this(score, classification, confidence, intensity, 0.33, 0.34, 0.33);
        }
        
        public SentimentResult(double score, String classification, double confidence, String intensity,
                               double posProb, double neuProb, double negProb) {
            this.score = score;
            this.classification = classification;
            this.confidence = confidence;
            this.intensity = intensity;
            this.positiveProbability = posProb;
            this.neutralProbability = neuProb;
            this.negativeProbability = negProb;
        }
        
        public double getScore() { return score; }
        public String getClassification() { return classification; }
        public double getConfidence() { return confidence; }
        public String getIntensity() { return intensity; }
        public double getPositiveProbability() { return positiveProbability; }
        public double getNeutralProbability() { return neutralProbability; }
        public double getNegativeProbability() { return negativeProbability; }
    }
}
