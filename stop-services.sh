#!/bin/bash

# ============================================
# Sentiment Dashboard - Stop Services Script
# Updated for Kafka 4.x (KRaft mode - No Zookeeper)
# ============================================

PROJECT_DIR="$HOME/sentiment-dashboard"
FLINK_HOME="$HOME/flink-1.18.0"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Stopping all services...${NC}\n"

# Stop React Dashboard
if [ -f "$PROJECT_DIR/logs/react.pid" ]; then
    PID=$(cat $PROJECT_DIR/logs/react.pid)
    kill $PID 2>/dev/null && echo -e "${GREEN}✓${NC} Stopped React Dashboard (PID: $PID)"
    rm $PROJECT_DIR/logs/react.pid
fi

# Stop WebSocket Server
if [ -f "$PROJECT_DIR/logs/websocket.pid" ]; then
    PID=$(cat $PROJECT_DIR/logs/websocket.pid)
    kill $PID 2>/dev/null && echo -e "${GREEN}✓${NC} Stopped WebSocket Server (PID: $PID)"
    rm $PROJECT_DIR/logs/websocket.pid
fi

# Stop Kafka Producer
if [ -f "$PROJECT_DIR/logs/producer.pid" ]; then
    PID=$(cat $PROJECT_DIR/logs/producer.pid)
    kill $PID 2>/dev/null && echo -e "${GREEN}✓${NC} Stopped Kafka Producer (PID: $PID)"
    rm $PROJECT_DIR/logs/producer.pid
fi

# Stop Flink
if [ -d "$FLINK_HOME" ]; then
    echo -e "${YELLOW}Stopping Flink cluster...${NC}"
    cd $FLINK_HOME
    ./bin/stop-cluster.sh
    echo -e "${GREEN}✓${NC} Stopped Flink cluster"
else
    echo -e "${YELLOW}⚠${NC} Flink not found at $FLINK_HOME"
fi

# Stop Redis
echo -e "${YELLOW}Stopping Redis...${NC}"
brew services stop redis
echo -e "${GREEN}✓${NC} Stopped Redis"

# Stop Kafka (KRaft mode - no Zookeeper to stop)
echo -e "${YELLOW}Stopping Kafka...${NC}"
brew services stop kafka
echo -e "${GREEN}✓${NC} Stopped Kafka"

echo ""
echo -e "${GREEN}All services stopped successfully!${NC}"
echo ""
echo -e "${CYAN}Note: Kafka data is preserved in /opt/homebrew/var/lib/kraft-combined-logs${NC}"
echo -e "${CYAN}To completely reset Kafka, run:${NC}"
echo -e "${CYAN}  brew services stop kafka && rm -rf /opt/homebrew/var/lib/kraft-combined-logs${NC}"
