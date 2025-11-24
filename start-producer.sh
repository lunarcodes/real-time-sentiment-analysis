#!/bin/bash

cd ~/sentiment-dashboard

echo "🚀 Starting Producer..."

# Check if JAR exists
if [ ! -f kafka-producer/target/twitter-producer.jar ]; then
    echo "❌ JAR not found. Building..."
    mvn clean package -DskipTests
fi

# Kill existing producer
pkill -f TwitterProducer

# Wait a bit
sleep 2

# Create logs directory
mkdir -p logs

# Start producer
nohup java -jar kafka-producer/target/twitter-producer.jar > logs/producer.log 2>&1 &
PID=$!

# Save PID
echo $PID > logs/producer.pid

# Wait and check
sleep 3

# Verify it's running
if ps -p $PID > /dev/null 2>&1; then
    echo "✅ Producer started (PID: $PID)"
    echo "📊 Watching logs..."
    tail -f logs/producer.log
else
    echo "❌ Producer failed to start"
    echo "Logs:"
    cat logs/producer.log
    exit 1
fi
