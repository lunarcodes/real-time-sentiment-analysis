# 🚀 Quick Reference Card - Sentiment Dashboard

## Installation (One-time setup)
```bash
chmod +x setup-mac.sh
./setup-mac.sh
source ~/.zshrc
cd ~/sentiment-dashboard
mvn clean install
```

## Start Everything
```bash
cd ~/sentiment-dashboard
chmod +x start-services.sh
./start-services.sh
```

## Stop Everything
```bash
cd ~/sentiment-dashboard
./stop-services.sh
```

## Access Points
- **Dashboard**: http://localhost:3000
- **Flink UI**: http://localhost:8081  
- **WebSocket**: ws://localhost:8080

## Common Commands

### Monitor Data Flow
```bash
# Watch Kafka messages
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic sentiment-analyzed --from-beginning

# Watch Redis updates
redis-cli MONITOR

# Check Flink jobs
curl http://localhost:8081/jobs | jq
```

### Debug Issues
```bash
# Check all services
lsof -i :2181  # Zookeeper
lsof -i :9092  # Kafka
lsof -i :6379  # Redis
lsof -i :8081  # Flink
lsof -i :8080  # WebSocket
lsof -i :3000  # React

# View logs
tail -f ~/sentiment-dashboard/logs/producer.log
tail -f ~/sentiment-dashboard/logs/websocket.log
tail -f ~/sentiment-dashboard/logs/react.log
tail -f ~/flink-1.18.0/log/flink-*-taskexecutor-*.log
```

### Kafka Management
```bash
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 \
  --describe --topic social-media-raw

# Consumer groups
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --list

# Reset consumer offset (if needed)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group flink-consumer-group \
  --reset-offsets --to-earliest --execute \
  --topic social-media-raw
```

### Flink Management
```bash
# List running jobs
$FLINK_HOME/bin/flink list

# Cancel a job
$FLINK_HOME/bin/flink cancel <job-id>

# Submit job manually
$FLINK_HOME/bin/flink run -d \
  ~/sentiment-dashboard/flink-jobs/target/flink-enrichment-job.jar

# Check savepoints
$FLINK_HOME/bin/flink list -s
```

### Performance Tuning
```bash
# Increase producer rate (edit TwitterProducer.java)
MESSAGES_PER_SECOND = 100  # Default: 50

# Increase Flink parallelism
$FLINK_HOME/bin/flink run -p 8 flink-enrichment-job.jar

# Monitor Kafka lag
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group flink-consumer-group
```

### Cleanup
```bash
# Delete all Kafka topics
for topic in $(kafka-topics --bootstrap-server localhost:9092 --list); do
  kafka-topics --bootstrap-server localhost:9092 --delete --topic $topic
done

# Clear Redis
redis-cli FLUSHALL

# Clear Flink checkpoints
rm -rf /tmp/flink-checkpoints/*
```

## Troubleshooting Quick Fixes

### Issue: "OutOfMemoryError" in Flink
```bash
# Edit flink-conf.yaml
taskmanager.memory.process.size: 4096m
jobmanager.memory.process.size: 2048m
```

### Issue: Kafka "Leader not available"
```bash
brew services restart kafka
sleep 10
./start-services.sh
```

### Issue: Redis connection refused
```bash
brew services restart redis
redis-cli PING  # Should return PONG
```

### Issue: WebSocket won't connect
```bash
# Check if port is in use
lsof -i :8080
# Kill if needed
kill $(lsof -t -i:8080)
# Restart
cd ~/sentiment-dashboard/websocket-server
java -jar target/websocket-server.jar
```

## Performance Metrics to Monitor

```bash
# CPU usage
top -o cpu

# Memory usage
top -o mem

# Disk I/O
iostat -d 5

# Network
nettop -m tcp

# Kafka metrics
kafka-broker-api-versions --bootstrap-server localhost:9092

# Redis stats
redis-cli INFO stats
```

## Data Volume Calculations

**Default Configuration (50 msg/sec)**:
- Messages per day: 4,320,000
- Storage per day: ~4GB (Kafka + Redis + Elasticsearch)
- Recommended disk: 100GB minimum

**High Volume (1000 msg/sec)**:
- Messages per day: 86,400,000
- Storage per day: ~80GB
- Recommended disk: 1TB with cleanup policy

## Emergency Stop
```bash
# If services won't stop normally
pkill -f kafka
pkill -f zookeeper  
pkill -f redis-server
pkill -f java
pkill -f node
$FLINK_HOME/bin/stop-cluster.sh
```

## Backup Commands
```bash
# Backup Kafka topics
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic sentiment-analyzed \
  --from-beginning \
  --max-messages 10000 > backup.json

# Backup Redis
redis-cli --rdb /tmp/dump.rdb

# Backup Flink savepoint
$FLINK_HOME/bin/flink savepoint <job-id> \
  file:///tmp/savepoints
```

---

**Keep this file handy for daily operations! 📌**
