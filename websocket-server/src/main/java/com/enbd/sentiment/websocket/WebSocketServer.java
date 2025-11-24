package com.enbd.sentiment.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple WebSocket Server that broadcasts Kafka messages to connected clients
 */
public class WebSocketServer {
    
    private static final int PORT = 8080;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("Starting WebSocket Server on port " + PORT + "...");
        
        // Start Kafka consumer in background thread
        Thread kafkaThread = new Thread(() -> consumeFromKafka());
        kafkaThread.setDaemon(true);
        kafkaThread.start();
        
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
        
        System.out.println("WebSocket Server started successfully!");
        System.out.println("Listening on ws://localhost:" + PORT);
        
        while (true) {
            selector.select();
            
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
        
        System.out.println("New client connected: " + client.getRemoteAddress());
        System.out.println("Total clients: " + clients.size());
    }
    
    /**
     * Read data from client (WebSocket handshake or messages)
     */
    private static void readFromClient(SocketChannel client) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = client.read(buffer);
            
            if (bytesRead == -1) {
                // Client disconnected
                clients.remove(client);
                client.close();
                System.out.println("Client disconnected. Remaining clients: " + clients.size());
                return;
            }
            
            buffer.flip();
            String data = new String(buffer.array(), 0, buffer.limit());
            
            // Handle WebSocket handshake
            if (data.contains("Upgrade: websocket")) {
                handleWebSocketHandshake(client, data);
            }
            
        } catch (IOException e) {
            try {
                clients.remove(client);
                client.close();
            } catch (IOException ex) {
                // Ignore
            }
            System.out.println("Error reading from client: " + e.getMessage());
        }
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
            System.out.println("WebSocket handshake completed");
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "websocket-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("sentiment-analyzed", "aggregated-metrics"));
        
        System.out.println("Kafka consumer started, listening for messages...");
        
        while (true) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    String message = record.value();
                    broadcastToClients(message);
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
        
        consumer.close();
    }
    
    /**
     * Broadcast message to all connected WebSocket clients
     */
    private static void broadcastToClients(String message) {
        if (clients.isEmpty()) {
            return;
        }
        
        try {
            // Wrap message in WebSocket frame
            byte[] messageBytes = message.getBytes();
            ByteBuffer frame = createWebSocketFrame(messageBytes);
            
            // Send to all clients
            List<SocketChannel> disconnected = new ArrayList<>();
            
            for (SocketChannel client : clients) {
                try {
                    frame.rewind();
                    client.write(frame);
                } catch (IOException e) {
                    disconnected.add(client);
                }
            }
            
            // Remove disconnected clients
            for (SocketChannel client : disconnected) {
                clients.remove(client);
                try {
                    client.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            
            if (!disconnected.isEmpty()) {
                System.out.println("Removed " + disconnected.size() + " disconnected clients");
            }
            
        } catch (Exception e) {
            System.err.println("Error broadcasting message: " + e.getMessage());
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
}
