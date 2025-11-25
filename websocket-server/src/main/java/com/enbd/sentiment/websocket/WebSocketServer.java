package com.enbd.sentiment.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced WebSocket Server that broadcasts Kafka messages to connected dashboard clients
 * 
 * Features:
 * - Wraps messages with type metadata for easy client parsing
 * - Tracks message statistics
 * - Sends heartbeat messages for connection health
 * - Handles multiple Kafka topics
 * - Graceful client connection management
 */
public class WebSocketServer {
    
    private static final int PORT = 8080;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
    private static final Set<SocketChannel> handshakeCompleted = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    
    // Statistics tracking
    private static final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private static final AtomicLong totalMessagesBroadcast = new AtomicLong(0);
    private static volatile long lastMessageTime = System.currentTimeMillis();
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║   Sentiment Dashboard WebSocket Server     ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Starting WebSocket Server on port " + PORT + "...");
        
        // Start Kafka consumer in background thread
        Thread kafkaThread = new Thread(() -> consumeFromKafka(), "KafkaConsumer");
        kafkaThread.setDaemon(true);
        kafkaThread.start();
        
        // Start heartbeat thread
        Thread heartbeatThread = new Thread(() -> sendHeartbeats(), "Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        
        // Start statistics reporter
        Thread statsThread = new Thread(() -> reportStatistics(), "Statistics");
        statsThread.setDaemon(true);
        statsThread.start();
        
        // Start WebSocket server
        try {
            startServer();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Start the WebSocket server
     */
    private static void startServer() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        
        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("✅ WebSocket Server started successfully!");
        System.out.println("📡 Listening on ws://localhost:" + PORT);
        System.out.println();
        System.out.println("Waiting for dashboard connections...");
        System.out.println();
        
        while (true) {
            selector.select(100); // 100ms timeout for responsiveness
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                if (!key.isValid()) {
                    continue;
                }
                
                if (key.isAcceptable()) {
                    acceptConnection(serverChannel, selector);
                } else if (key.isReadable()) {
                    readFromClient((SocketChannel) key.channel());
                }
            }
        }
    }
    
    /**
     * Accept new client connection
     */
    private static void acceptConnection(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        clients.add(client);
        
        System.out.println("🔗 New client connected: " + client.getRemoteAddress());
        System.out.println("   Total clients: " + clients.size());
    }
    
    /**
     * Read data from client (WebSocket handshake or messages)
     */
    private static void readFromClient(SocketChannel client) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            int bytesRead = client.read(buffer);
            
            if (bytesRead == -1) {
                // Client disconnected
                removeClient(client);
                return;
            }
            
            buffer.flip();
            String data = new String(buffer.array(), 0, buffer.limit());
            
            // Handle WebSocket handshake
            if (data.contains("Upgrade: websocket")) {
                handleWebSocketHandshake(client, data);
            } else if (handshakeCompleted.contains(client)) {
                // Handle WebSocket frames (ping/pong, close, etc.)
                handleWebSocketFrame(client, buffer);
            }
            
        } catch (IOException e) {
            removeClient(client);
        }
    }
    
    /**
     * Handle WebSocket data frames
     */
    private static void handleWebSocketFrame(SocketChannel client, ByteBuffer buffer) {
        try {
            if (buffer.remaining() < 2) return;
            
            byte firstByte = buffer.get(0);
            int opcode = firstByte & 0x0F;
            
            switch (opcode) {
                case 0x8: // Close frame
                    System.out.println("📴 Client sent close frame");
                    removeClient(client);
                    break;
                case 0x9: // Ping frame
                    // Send pong response
                    sendPong(client);
                    break;
                case 0xA: // Pong frame
                    // Client responded to our ping, connection is healthy
                    break;
                default:
                    // Text or binary frame - ignore for now
                    break;
            }
        } catch (Exception e) {
            // Ignore frame parsing errors
        }
    }
    
    /**
     * Send pong response to client
     */
    private static void sendPong(SocketChannel client) {
        try {
            ByteBuffer pong = ByteBuffer.allocate(2);
            pong.put((byte) 0x8A); // Pong frame
            pong.put((byte) 0x00); // No payload
            pong.flip();
            client.write(pong);
        } catch (IOException e) {
            removeClient(client);
        }
    }
    
    /**
     * Remove disconnected client
     */
    private static void removeClient(SocketChannel client) {
        clients.remove(client);
        handshakeCompleted.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            // Ignore
        }
        System.out.println("❌ Client disconnected. Remaining clients: " + clients.size());
    }
    
    /**
     * Handle WebSocket handshake
     */
    private static void handleWebSocketHandshake(SocketChannel client, String request) throws IOException {
        String key = null;
        
        // Extract Sec-WebSocket-Key
        for (String line : request.split("\r\n")) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                key = line.substring("Sec-WebSocket-Key:".length()).trim();
                break;
            }
        }
        
        if (key != null) {
            String acceptKey = generateAcceptKey(key);
            
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            
            client.write(ByteBuffer.wrap(response.getBytes()));
            handshakeCompleted.add(client);
            System.out.println("✅ WebSocket handshake completed for client");
            
            // Send welcome message
            sendWelcomeMessage(client);
        }
    }
    
    /**
     * Send welcome message to newly connected client
     */
    private static void sendWelcomeMessage(SocketChannel client) {
        try {
            ObjectNode welcome = MAPPER.createObjectNode();
            welcome.put("message_type", "connection_established");
            welcome.put("server_time", Instant.now().toString());
            welcome.put("total_messages_processed", totalMessagesProcessed.get());
            welcome.put("connected_clients", clients.size());
            
            ObjectNode topics = welcome.putObject("subscribed_topics");
            topics.put("sentiment", "sentiment-analyzed");
            topics.put("aggregated", "aggregated-metrics");
            
            String json = MAPPER.writeValueAsString(welcome);
            sendToClient(client, json);
            
        } catch (Exception e) {
            System.err.println("Error sending welcome message: " + e.getMessage());
        }
    }
    
    /**
     * Generate WebSocket accept key
     */
    private static String generateAcceptKey(String key) {
        try {
            String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest((key + magic).getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Consume messages from Kafka and broadcast to clients
     */
    private static void consumeFromKafka() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "websocket-consumer-group-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Arrays.asList("sentiment-analyzed", "aggregated-metrics"));
            
            System.out.println("✅ Kafka consumer started");
            System.out.println("📥 Subscribed to topics: sentiment-analyzed, aggregated-metrics");
            System.out.println();
            
            while (true) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        totalMessagesProcessed.incrementAndGet();
                        lastMessageTime = System.currentTimeMillis();
                        
                        String wrappedMessage = wrapMessage(record.topic(), record.value());
                        if (wrappedMessage != null) {
                            broadcastToClients(wrappedMessage);
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error consuming from Kafka: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to create Kafka consumer: " + e.getMessage());
            System.err.println("Make sure Kafka is running on localhost:9092");
        }
    }
    
    /**
     * Wrap Kafka message with metadata for dashboard consumption
     */
    private static String wrapMessage(String topic, String message) {
        try {
            JsonNode originalMessage = MAPPER.readTree(message);
            ObjectNode wrapped = MAPPER.createObjectNode();
            
            // Add message type based on topic
            if (topic.equals("sentiment-analyzed")) {
                wrapped.put("message_type", "sentiment_update");
            } else if (topic.equals("aggregated-metrics")) {
                wrapped.put("message_type", "aggregation_update");
            } else {
                wrapped.put("message_type", "unknown");
            }
            
            // Add metadata
            wrapped.put("source_topic", topic);
            wrapped.put("server_timestamp", Instant.now().toString());
            wrapped.put("sequence_number", totalMessagesProcessed.get());
            
            // Add original payload
            wrapped.set("payload", originalMessage);
            
            return MAPPER.writeValueAsString(wrapped);
            
        } catch (Exception e) {
            // If wrapping fails, send original message
            return message;
        }
    }
    
    /**
     * Broadcast message to all connected WebSocket clients
     */
    private static void broadcastToClients(String message) {
        if (clients.isEmpty()) {
            return;
        }
        
        List<SocketChannel> disconnected = new ArrayList<>();
        
        for (SocketChannel client : clients) {
            if (!handshakeCompleted.contains(client)) {
                continue;
            }
            
            try {
                sendToClient(client, message);
                totalMessagesBroadcast.incrementAndGet();
            } catch (Exception e) {
                disconnected.add(client);
            }
        }
        
        // Remove disconnected clients
        for (SocketChannel client : disconnected) {
            removeClient(client);
        }
    }
    
    /**
     * Send message to a specific client
     */
    private static void sendToClient(SocketChannel client, String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        ByteBuffer frame = createWebSocketFrame(messageBytes);
        
        while (frame.hasRemaining()) {
            client.write(frame);
        }
    }
    
    /**
     * Create WebSocket frame for text message
     */
    private static ByteBuffer createWebSocketFrame(byte[] message) {
        int length = message.length;
        ByteBuffer frame;
        
        if (length <= 125) {
            frame = ByteBuffer.allocate(2 + length);
            frame.put((byte) 0x81); // FIN + Text frame
            frame.put((byte) length);
        } else if (length <= 65535) {
            frame = ByteBuffer.allocate(4 + length);
            frame.put((byte) 0x81);
            frame.put((byte) 126);
            frame.putShort((short) length);
        } else {
            frame = ByteBuffer.allocate(10 + length);
            frame.put((byte) 0x81);
            frame.put((byte) 127);
            frame.putLong(length);
        }
        
        frame.put(message);
        frame.flip();
        return frame;
    }
    
    /**
     * Send heartbeat messages to keep connections alive
     */
    private static void sendHeartbeats() {
        while (true) {
            try {
                Thread.sleep(30000); // Every 30 seconds
                
                if (!clients.isEmpty()) {
                    ObjectNode heartbeat = MAPPER.createObjectNode();
                    heartbeat.put("message_type", "heartbeat");
                    heartbeat.put("timestamp", Instant.now().toString());
                    heartbeat.put("connected_clients", clients.size());
                    heartbeat.put("total_messages_processed", totalMessagesProcessed.get());
                    heartbeat.put("total_messages_broadcast", totalMessagesBroadcast.get());
                    
                    String json = MAPPER.writeValueAsString(heartbeat);
                    broadcastToClients(json);
                }
                
            } catch (Exception e) {
                // Ignore heartbeat errors
            }
        }
    }
    
    /**
     * Report statistics periodically
     */
    private static void reportStatistics() {
        long lastReportedProcessed = 0;
        long lastReportTime = System.currentTimeMillis();
        
        while (true) {
            try {
                Thread.sleep(10000); // Every 10 seconds
                
                long currentProcessed = totalMessagesProcessed.get();
                long currentTime = System.currentTimeMillis();
                long elapsed = (currentTime - lastReportTime) / 1000;
                
                if (elapsed > 0) {
                    long messagesInPeriod = currentProcessed - lastReportedProcessed;
                    double rate = messagesInPeriod / (double) elapsed;
                    
                    System.out.println("📊 Stats: " + 
                        "Clients: " + clients.size() + " | " +
                        "Processed: " + currentProcessed + " | " +
                        "Rate: " + String.format("%.1f", rate) + " msg/s | " +
                        "Last msg: " + ((currentTime - lastMessageTime) / 1000) + "s ago");
                }
                
                lastReportedProcessed = currentProcessed;
                lastReportTime = currentTime;
                
            } catch (Exception e) {
                // Ignore stats errors
            }
        }
    }
}
