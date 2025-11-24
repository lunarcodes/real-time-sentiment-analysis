#!/bin/bash

echo "🛑 Stopping all services..."

# Stop Producer
if [ -f ~/sentiment-dashboard/logs/producer.pid ]; then
    kill $(cat ~/sentiment-dashboard/logs/producer.pid) 2>/dev/null
    rm ~/sentiment-dashboard/logs/producer.pid
    echo "✅ Stopped Producer"
fi

# Stop WebSocket
if [ -f ~/sentiment-dashboard/logs/websocket.pid ]; then
    kill $(cat ~/sentiment-dashboard/logs/websocket.pid) 2>/dev/null
    rm ~/sentiment-dashboard/logs/websocket.pid
    echo "✅ Stopped WebSocket"
fi

# Stop React (if running)
if [ -f ~/sentiment-dashboard/logs/react.pid ]; then
    kill $(cat ~/sentiment-dashboard/logs/react.pid) 2>/dev/null
    rm ~/sentiment-dashboard/logs/react.pid
    echo "✅ Stopped React"
fi

# Stop Flink
~/flink-1.18.0/bin/stop-cluster.sh
echo "✅ Stopped Flink"

# Stop Kafka
brew services stop kafka
echo "✅ Stopped Kafka"

# Stop Redis
brew services stop redis
echo "✅ Stopped Redis"

echo ""
echo "✅ All services stopped!"
