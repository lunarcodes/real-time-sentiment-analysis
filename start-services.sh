#!/bin/bash

# ============================================
# Sentiment Dashboard - Deployment Script
# Updated for Kafka 4.x (KRaft mode - No Zookeeper)
# ============================================

set -e

PROJECT_DIR="$HOME/sentiment-dashboard"
FLINK_HOME="$HOME/flink-1.18.0"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   Sentiment Dashboard Deployment Tool     ║${NC}"
echo -e "${CYAN}║        (Kafka 4.x KRaft Mode)            ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════╝${NC}"
echo ""

# Function to check if a service is running
check_service() {
    local service=$1
    local port=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        echo -e "${GREEN}✓ $service is running on port $port${NC}"
        return 0
    else
        echo -e "${RED}✗ $service is not running on port $port${NC}"
        return 1
    fi
}

# Step 1: Initialize and Start Kafka (KRaft mode)
start_kafka() {
    echo -e "\n${YELLOW}[1/6] Starting Kafka (KRaft mode)...${NC}"
    
    if check_service "Kafka" 9092; then
        echo "  Kafka already running"
    else
        # Check if Kafka storage needs to be initialized
        if [ ! -f "/opt/homebrew/var/lib/kraft-combined-logs/meta.properties" ]; then
            echo "  Initializing Kafka storage (first-time setup)..."
            
            # Stop Kafka if somehow running
            brew services stop kafka 2>/dev/null || true
            
            # Clear any old data
            rm -rf /opt/homebrew/var/lib/kraft-combined-logs 2>/dev/null || true
            
            # Generate cluster ID and format storage
            KAFKA_CLUSTER_ID=$(kafka-storage random-uuid)
            echo "  Generated Cluster ID: $KAFKA_CLUSTER_ID"
            
            kafka-storage format -t $KAFKA_CLUSTER_ID \
                -c /opt/homebrew/etc/kafka/server.properties \
                --standalone
            
            echo -e "  ${GREEN}✓ Kafka storage initialized${NC}"
        fi
        
        # Start Kafka
        brew services start kafka
        echo "  Waiting for Kafka to start (20 seconds)..."
        sleep 20
        
        if check_service "Kafka" 9092; then
            echo -e "  ${GREEN}Kafka started successfully${NC}"
        else
            echo -e "  ${RED}Failed to start Kafka${NC}"
            echo -e "  ${YELLOW}Check logs: tail -f /opt/homebrew/var/log/kafka/server.log${NC}"
            exit 1
        fi
    fi
}

# Step 2: Create Kafka Topics
create_kafka_topics() {
    echo -e "\n${YELLOW}[2/6] Creating Kafka Topics...${NC}"
    
    topics=(
        "social-media-raw:12:1"
        "enriched-messages:12:1"
        "sentiment-analyzed:12:1"
        "aggregated-metrics:6:1"
        "alerts:3:1"
    )
    
    for topic_config in "${topics[@]}"; do
        IFS=':' read -r topic partitions replication <<< "$topic_config"
        
        if kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -q "^${topic}$"; then
            echo -e "  ${GREEN}✓${NC} Topic exists: $topic"
        else
            kafka-topics --bootstrap-server localhost:9092 \
                --create \
                --topic $topic \
                --partitions $partitions \
                --replication-factor $replication \
                --config compression.type=snappy \
                --config retention.ms=604800000 2>/dev/null
            echo -e "  ${GREEN}✓${NC} Created topic: $topic"
        fi
    done
}

# Step 3: Start Redis
start_redis() {
    echo -e "\n${YELLOW}[3/6] Starting Redis...${NC}"
    if check_service "Redis" 6379; then
        echo "  Redis already running"
    else
        brew services start redis
        sleep 3
        if check_service "Redis" 6379; then
            echo -e "  ${GREEN}Redis started successfully${NC}"
        else
            echo -e "  ${RED}Failed to start Redis${NC}"
            exit 1
        fi
    fi
}

# Step 4: Start Flink Cluster
start_flink() {
    echo -e "\n${YELLOW}[4/6] Starting Flink Cluster...${NC}"
    
    # Check if FLINK_HOME exists
    if [ ! -d "$FLINK_HOME" ]; then
        echo -e "  ${RED}Flink not found at $FLINK_HOME${NC}"
        echo -e "  ${YELLOW}Please install Flink first or update FLINK_HOME${NC}"
        echo -e "  ${YELLOW}Download from: https://flink.apache.org/downloads.html${NC}"
        exit 1
    fi
    
    if check_service "Flink JobManager" 8081; then
        echo "  Flink already running"
    else
        cd $FLINK_HOME
        ./bin/start-cluster.sh
        sleep 5
        if check_service "Flink JobManager" 8081; then
            echo -e "  ${GREEN}Flink started successfully${NC}"
            echo -e "  Dashboard: ${CYAN}http://localhost:8081${NC}"
        else
            echo -e "  ${RED}Failed to start Flink${NC}"
            exit 1
        fi
    fi
}

# Step 5: Build and Deploy Applications
build_and_deploy() {
    echo -e "\n${YELLOW}[5/6] Building Applications...${NC}"
    
    cd $PROJECT_DIR
    
    # Build all modules
    echo "  Building Maven projects..."
    mvn clean package -DskipTests -q
    
    if [ $? -eq 0 ]; then
        echo -e "  ${GREEN}✓ Build successful${NC}"
    else
        echo -e "  ${RED}✗ Build failed${NC}"
        exit 1
    fi
    
    # Submit Flink jobs (if they exist)
    echo -e "\n  Submitting Flink jobs..."
    
    # Check if jobs already running
    EXISTING_JOBS=$(curl -s http://localhost:8081/jobs 2>/dev/null | grep -o '"id":"[^"]*"' | wc -l || echo "0")
    
    if [ "$EXISTING_JOBS" -ge 3 ]; then
        echo -e "  ${YELLOW}⚠ Flink jobs already running. Skipping deployment.${NC}"
        echo -e "  ${YELLOW}  To redeploy, cancel existing jobs first.${NC}"
    else
        # Enrichment Job
        if [ -f "$PROJECT_DIR/flink-jobs/target/flink-enrichment-job.jar" ]; then
            $FLINK_HOME/bin/flink run -d \
                $PROJECT_DIR/flink-jobs/target/flink-enrichment-job.jar 2>/dev/null
            echo -e "  ${GREEN}✓${NC} Submitted Enrichment Job"
        else
            echo -e "  ${YELLOW}⚠${NC} flink-enrichment-job.jar not found, skipping..."
        fi
        
        # Sentiment Analysis Job
        if [ -f "$PROJECT_DIR/flink-jobs/target/flink-sentiment-job.jar" ]; then
            $FLINK_HOME/bin/flink run -d \
                $PROJECT_DIR/flink-jobs/target/flink-sentiment-job.jar 2>/dev/null
            echo -e "  ${GREEN}✓${NC} Submitted Sentiment Analysis Job"
        else
            echo -e "  ${YELLOW}⚠${NC} flink-sentiment-job.jar not found, skipping..."
        fi
        
        # Aggregation Job
        if [ -f "$PROJECT_DIR/flink-jobs/target/flink-aggregation-job.jar" ]; then
            $FLINK_HOME/bin/flink run -d \
                $PROJECT_DIR/flink-jobs/target/flink-aggregation-job.jar 2>/dev/null
            echo -e "  ${GREEN}✓${NC} Submitted Aggregation Job"
        else
            echo -e "  ${YELLOW}⚠${NC} flink-aggregation-job.jar not found, skipping..."
        fi
    fi
}

# Step 6: Start Producer and WebSocket Server
start_applications() {
    echo -e "\n${YELLOW}[6/6] Starting Applications...${NC}"
    
    # Create logs directory if not exists
    mkdir -p $PROJECT_DIR/logs
    
    # Start Kafka Producer in background
    echo "  Starting Twitter Producer..."
    if [ -f "$PROJECT_DIR/kafka-producer/target/twitter-producer.jar" ]; then
        cd $PROJECT_DIR/kafka-producer
        nohup java -jar target/twitter-producer.jar > $PROJECT_DIR/logs/producer.log 2>&1 &
        echo $! > $PROJECT_DIR/logs/producer.pid
        echo -e "  ${GREEN}✓${NC} Twitter Producer started (PID: $(cat $PROJECT_DIR/logs/producer.pid))"
    else
        echo -e "  ${YELLOW}⚠${NC} twitter-producer.jar not found, skipping..."
    fi
    
    # Start WebSocket Server
    echo "  Starting WebSocket Server..."
    if [ -f "$PROJECT_DIR/websocket-server/target/websocket-server.jar" ]; then
        cd $PROJECT_DIR/websocket-server
        nohup java -jar target/websocket-server.jar > $PROJECT_DIR/logs/websocket.log 2>&1 &
        echo $! > $PROJECT_DIR/logs/websocket.pid
        echo -e "  ${GREEN}✓${NC} WebSocket Server started (PID: $(cat $PROJECT_DIR/logs/websocket.pid))"
    else
        echo -e "  ${YELLOW}⚠${NC} websocket-server.jar not found, skipping..."
    fi
    
    # Start React Dashboard
    echo "  Starting React Dashboard..."
    if [ -d "$PROJECT_DIR/react-dashboard" ] && [ -f "$PROJECT_DIR/react-dashboard/package.json" ]; then
        cd $PROJECT_DIR/react-dashboard
        npm install > /dev/null 2>&1
        nohup npm start > $PROJECT_DIR/logs/react.log 2>&1 &
        echo $! > $PROJECT_DIR/logs/react.pid
        echo -e "  ${GREEN}✓${NC} React Dashboard started (PID: $(cat $PROJECT_DIR/logs/react.pid))"
    else
        echo -e "  ${YELLOW}⚠${NC} React dashboard not configured, skipping..."
    fi
}

# Main execution
main() {
    start_kafka
    create_kafka_topics
    start_redis
    start_flink
    build_and_deploy
    start_applications
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║     All Services Started Successfully!     ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${CYAN}Access Points:${NC}"
    echo -e "  • Flink Dashboard:     ${CYAN}http://localhost:8081${NC}"
    echo -e "  • React Dashboard:     ${CYAN}http://localhost:3000${NC}"
    echo -e "  • WebSocket Server:    ${CYAN}ws://localhost:8080${NC}"
    echo ""
    echo -e "${CYAN}Monitoring:${NC}"
    echo -e "  • Producer logs:       tail -f $PROJECT_DIR/logs/producer.log"
    echo -e "  • WebSocket logs:      tail -f $PROJECT_DIR/logs/websocket.log"
    echo -e "  • React logs:          tail -f $PROJECT_DIR/logs/react.log"
    echo -e "  • Kafka logs:          tail -f /opt/homebrew/var/log/kafka/server.log"
    echo ""
    echo -e "${CYAN}Kafka Commands:${NC}"
    echo -e "  • List topics:         kafka-topics --bootstrap-server localhost:9092 --list"
    echo -e "  • Watch messages:      kafka-console-consumer --bootstrap-server localhost:9092 --topic social-media-raw"
    echo ""
    echo -e "${CYAN}To stop all services:${NC}"
    echo -e "  ./stop-services.sh"
    echo ""
}

# Run main
main
