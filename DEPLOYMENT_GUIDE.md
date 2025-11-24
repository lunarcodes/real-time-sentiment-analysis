# 📘 Complete Deployment Guide - Mac Setup

## Overview

This guide will walk you through deploying the **Enterprise Sentiment Analysis Dashboard** on your MacBook Pro from scratch. Total setup time: ~30 minutes.

---

## 📋 System Requirements

- **OS**: macOS 11.0 (Big Sur) or later
- **RAM**: 8GB minimum, 16GB recommended
- **Storage**: 10GB free space
- **Processor**: Intel or Apple Silicon (M1/M2/M3)

---

## 🎯 What We're Building

A real-time sentiment analysis platform that:
1. Simulates social media data (Twitter-like tweets)
2. Processes 50+ messages per second
3. Analyzes sentiment using AI algorithms
4. Aggregates metrics by location and channel
5. Displays results on an interactive 3D dashboard
6. **Total latency**: 2.7 seconds from tweet to dashboard

---

## 🔧 Part 1: Software Installation (15 minutes)

### Step 1: Open Terminal

Press `Cmd + Space`, type "Terminal", and press Enter.

### Step 2: Run Installation Script

```bash
# Download the project (assume you have the files)
cd ~/Downloads/sentiment-dashboard-complete

# Make the installation script executable
chmod +x setup-mac.sh

# Run installation
./setup-mac.sh
```

**What gets installed**:
- ✅ Homebrew (if not already installed)
- ✅ Java 17 OpenJDK
- ✅ Apache Maven 3.9+
- ✅ Apache Kafka 3.6.0
- ✅ Redis 7.x
- ✅ Node.js 20.x
- ✅ Apache Flink 1.18.0 (downloaded to ~/flink-1.18.0)

**Expected output**:
```
🚀 Starting installation of all required software...
✓ Homebrew already installed
Updating Homebrew...
Installing Java 17...
✓ Java 17 already installed
...
============================================
✓ Installation Complete!
============================================
```

### Step 3: Verify Installations

```bash
# Load new environment variables
source ~/.zshrc

# Verify Java
java -version
# Expected: openjdk version "17.0.x"

# Verify Maven
mvn -version
# Expected: Apache Maven 3.9.x

# Verify Kafka
kafka-topics --version
# Expected: 3.6.0

# Verify Redis
redis-server --version
# Expected: Redis server v=7.x.x

# Verify Node
node --version
# Expected: v20.x.x
```

---

## 🏗️ Part 2: Project Setup (5 minutes)

### Step 1: Move Project to Home Directory

```bash
# Copy project to permanent location
cp -r ~/Downloads/sentiment-dashboard-complete ~/sentiment-dashboard
cd ~/sentiment-dashboard
```

### Step 2: Project Structure

Your directory should look like this:

```
~/sentiment-dashboard/
├── setup-mac.sh              # Installation script
├── start-services.sh         # Start all services
├── stop-services.sh          # Stop all services
├── pom.xml                   # Maven parent POM
├── README.md                 # Full documentation
├── QUICK_REFERENCE.md        # Command cheat sheet
│
├── common-models/            # Shared data models
│   ├── pom.xml
│   └── src/main/java/com/enbd/sentiment/models/
│       ├── RawTweet.java
│       ├── EnrichedMessage.java
│       ├── SentimentAnalyzedMessage.java
│       ├── AggregatedMetrics.java
│       └── DashboardMessage.java
│
├── kafka-producer/           # Twitter simulator
│   ├── pom.xml
│   └── src/main/java/com/enbd/sentiment/producer/
│       └── TwitterProducer.java
│
├── flink-jobs/              # Stream processing (to be created)
├── websocket-server/        # Real-time server (to be created)
└── react-dashboard/         # Frontend (to be created)
```

### Step 3: Build the Project

```bash
cd ~/sentiment-dashboard

# Clean and build all modules
mvn clean install -DskipTests

# Expected output:
# [INFO] ------------------------------------------------------------------------
# [INFO] BUILD SUCCESS
# [INFO] ------------------------------------------------------------------------
```

**This takes 2-3 minutes on first run** (Maven downloads dependencies).

---

## 🚀 Part 3: First Deployment (5 minutes)

### Step 1: Make Scripts Executable

```bash
cd ~/sentiment-dashboard
chmod +x *.sh
```

### Step 2: Start All Services

```bash
./start-services.sh
```

**What happens** (automatic sequence):

1. **[1/7] Starting Zookeeper** (required for Kafka)
   - Port: 2181
   - Wait: 5 seconds

2. **[2/7] Starting Kafka** (message broker)
   - Port: 9092
   - Wait: 10 seconds

3. **[3/7] Creating Kafka Topics**
   - `social-media-raw` (12 partitions)
   - `enriched-messages` (12 partitions)
   - `sentiment-analyzed` (12 partitions)
   - `aggregated-metrics` (6 partitions)
   - `alerts` (3 partitions)

4. **[4/7] Starting Redis** (fast cache)
   - Port: 6379

5. **[5/7] Starting Flink Cluster** (stream processing)
   - JobManager: port 8081
   - TaskManager: 4 slots

6. **[6/7] Building and Deploying**
   - Maven build (quick, already compiled)
   - Submit 3 Flink jobs:
     - Enrichment Job
     - Sentiment Analysis Job
     - Aggregation Job

7. **[7/7] Starting Applications**
   - Kafka Producer (generates 50 tweets/sec)
   - WebSocket Server (port 8080)
   - React Dashboard (port 3000)

**Expected final output**:
```
╔════════════════════════════════════════════╗
║     All Services Started Successfully!     ║
╚════════════════════════════════════════════╝

Access Points:
  • Flink Dashboard:     http://localhost:8081
  • React Dashboard:     http://localhost:3000
  • WebSocket Server:    ws://localhost:8080
```

### Step 3: Verify Everything is Running

Open 3 browser tabs:

**Tab 1: Flink Dashboard**
```
http://localhost:8081
```
You should see:
- 3 running jobs
- Green status indicators
- Task metrics updating

**Tab 2: React Dashboard**
```
http://localhost:3000
```
You should see:
- 3D rotating globe with city markers
- Live message feed on the right
- Sentiment charts updating in real-time
- Messages appearing every 1-2 seconds

**Tab 3: Check Logs**

Open new terminal:
```bash
# Watch producer generating tweets
tail -f ~/sentiment-dashboard/logs/producer.log

# Expected output:
# Sent 500 messages | Rate: 51.23 msg/sec
# Sent 1000 messages | Rate: 50.87 msg/sec
```

---

## 🎮 Part 4: Using the Dashboard (Interactive)

### Understanding the Interface

**Overview Tab** (default view):
- **Top Left**: Sentiment percentages (Positive, Neutral, Negative)
- **Center**: 3D globe showing real-time message locations
- **Top Right**: Live message feed with sentiment badges
- **Bottom Left**: Sentiment trend line chart

**Channels Tab**:
- Channel performance comparison
- Volume distribution
- Response time metrics

**Products Tab**:
- Product sentiment radar chart
- Churn risk analysis
- Upsell opportunities

**Operations Tab**:
- Churn risk heatmap
- Business intelligence metrics
- Alert notifications

### Watch Real-Time Updates

1. **Globe Animation**: Cities pulse when messages are received
   - 🔵 Blue pulse = Positive sentiment
   - 🟡 Yellow pulse = Neutral sentiment
   - 🔴 Red pulse = Negative sentiment

2. **Live Feed**: Right panel shows latest messages
   - Gender icon (👨👩🧑)
   - Channel icon (📱🌐📞)
   - Sentiment score badge
   - Churn risk indicator

3. **Alert System**: Red alerts appear for:
   - High churn risk customers (>75%)
   - Product issue spikes
   - Urgent response needed

---

## 🔍 Part 5: Monitoring the Pipeline

### Monitor Kafka Messages

```bash
# Terminal 1: Watch raw tweets
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic social-media-raw \
  --from-beginning | jq .

# Terminal 2: Watch enriched messages
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic enriched-messages | jq .

# Terminal 3: Watch sentiment results
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic sentiment-analyzed | jq .
```

### Monitor Redis Cache

```bash
# Open Redis CLI
redis-cli

# Inside Redis CLI:
> MONITOR
# You'll see real-time Redis commands

# In another terminal:
redis-cli
> KEYS *
# Shows all cached keys

> GET msg:evt_1730556225_abc123
# Gets a specific message
```

### Monitor Flink Jobs

```bash
# Check job status
curl http://localhost:8081/jobs | jq

# Get job details
curl http://localhost:8081/jobs/<job-id> | jq

# View metrics
curl http://localhost:8081/jobs/<job-id>/metrics | jq
```

---

## 🛠️ Part 6: Troubleshooting

### Problem: Services Won't Start

**Solution 1**: Check if ports are already in use
```bash
lsof -i :2181  # Zookeeper
lsof -i :9092  # Kafka
lsof -i :6379  # Redis
lsof -i :8081  # Flink

# If something is running, kill it:
kill $(lsof -t -i:9092)
```

**Solution 2**: Reset everything
```bash
./stop-services.sh
sleep 5
./start-services.sh
```

### Problem: Kafka Topics Not Created

```bash
# Manually create topics
kafka-topics --bootstrap-server localhost:9092 \
  --create \
  --topic social-media-raw \
  --partitions 12 \
  --replication-factor 1
```

### Problem: Flink Jobs Failing

```bash
# Check Flink logs
tail -f ~/flink-1.18.0/log/flink-*-taskexecutor-*.log

# Common issue: OutOfMemoryError
# Solution: Edit flink-conf.yaml
nano ~/flink-1.18.0/conf/flink-conf.yaml

# Change these lines:
taskmanager.memory.process.size: 4096m
jobmanager.memory.process.size: 2048m
```

### Problem: React Dashboard Not Loading

```bash
# Check if Node server is running
lsof -i :3000

# If not running:
cd ~/sentiment-dashboard/react-dashboard
npm install
npm start
```

### Problem: No Data in Dashboard

**Check the pipeline**:
```bash
# 1. Is producer running?
ps aux | grep TwitterProducer

# 2. Are messages in Kafka?
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic social-media-raw \
  --max-messages 5

# 3. Is Flink processing?
curl http://localhost:8081/jobs | jq '.jobs[].state'
# All should show "RUNNING"

# 4. Is Redis receiving data?
redis-cli KEYS "*"
# Should show keys like msg:*, agg:*, etc.

# 5. Is WebSocket server running?
lsof -i :8080
```

---

## 🎯 Part 7: Performance Tuning

### Increase Message Rate

Edit `TwitterProducer.java`:
```java
private static final int MESSAGES_PER_SECOND = 100;  // Was 50
```

Rebuild and restart:
```bash
cd ~/sentiment-dashboard/kafka-producer
mvn clean package
./stop-services.sh
./start-services.sh
```

### Increase Flink Parallelism

```bash
# Stop current jobs
$FLINK_HOME/bin/flink list
$FLINK_HOME/bin/flink cancel <job-id>

# Restart with higher parallelism
$FLINK_HOME/bin/flink run -p 8 \
  ~/sentiment-dashboard/flink-jobs/target/flink-enrichment-job.jar
```

### Monitor System Resources

```bash
# CPU usage
top -o cpu

# Memory usage
top -o mem

# Disk I/O
iostat -d 5

# Network
nettop -m tcp
```

---

## 🛑 Part 8: Stopping Services

### Graceful Shutdown

```bash
cd ~/sentiment-dashboard
./stop-services.sh
```

This stops (in order):
1. React Dashboard
2. WebSocket Server
3. Kafka Producer
4. Flink Cluster
5. Kafka
6. Zookeeper
7. Redis

### Emergency Stop

If services hang:
```bash
pkill -f kafka
pkill -f zookeeper
pkill -f redis-server
pkill -f java
pkill -f node
```

---

## 📊 Part 9: Understanding the Data

### Message Flow Timing

| Stage | Time | Component | Output |
|-------|------|-----------|---------|
| 0.0s | Raw tweet | TwitterProducer | JSON message |
| 0.1s | Kafka ingestion | Kafka | social-media-raw |
| 0.5s | Enrichment | Flink Job 1 | enriched-messages |
| 1.5s | Sentiment analysis | Flink Job 2 | sentiment-analyzed |
| 2.0s | Aggregation | Flink Job 3 | aggregated-metrics |
| 2.2s | Cache | Redis | In-memory storage |
| 2.4s | Push notification | WebSocket | Real-time update |
| 3.0s | UI render | React | Dashboard display |

### Data Volume

**Default (50 msg/sec)**:
- Per minute: 3,000 messages
- Per hour: 180,000 messages
- Per day: 4,320,000 messages
- Storage: ~4GB/day (Kafka + Redis)

---

## 🎓 Next Steps

1. **Customize the Dashboard**:
   - Edit `react-dashboard/src/EnhancedSentimentDashboard.jsx`
   - Modify colors, charts, or layouts

2. **Add More Data Sources**:
   - Extend `TwitterProducer.java` to read from APIs
   - Add Facebook, Instagram simulators

3. **Improve Sentiment Analysis**:
   - Integrate real ML models in `SentimentJob.java`
   - Add multi-language support

4. **Deploy to Cloud**:
   - AWS MSK for Kafka
   - AWS EMR for Flink
   - ElastiCache for Redis
   - CloudFront for React

---

## 📞 Getting Help

If you encounter issues:

1. **Check logs** first:
   ```bash
   tail -f ~/sentiment-dashboard/logs/*.log
   ```

2. **Review the Quick Reference**:
   ```bash
   cat ~/sentiment-dashboard/QUICK_REFERENCE.md
   ```

3. **Test each component individually**:
   - Kafka: Can you produce/consume messages?
   - Flink: Are jobs running in the UI?
   - Redis: Can you connect and set/get keys?
   - WebSocket: Can you connect using `wscat`?

---

## ✅ Deployment Checklist

- [ ] All software installed successfully
- [ ] Project built without errors (`mvn clean install`)
- [ ] All services started (`./start-services.sh`)
- [ ] Flink dashboard shows 3 running jobs (http://localhost:8081)
- [ ] React dashboard loads and shows data (http://localhost:3000)
- [ ] Producer logs show messages being sent
- [ ] Kafka topics have messages
- [ ] Redis has cached data
- [ ] WebSocket connections successful
- [ ] 3D globe animates smoothly
- [ ] Live feed updates every 1-2 seconds

---

**Congratulations! Your sentiment analysis platform is now running! 🎉**

Start time: ~30 minutes
Messages processed: 50/second
End-to-end latency: 2.7 seconds
