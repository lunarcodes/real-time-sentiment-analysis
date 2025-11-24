#!/bin/bash

# ============================================
# Enterprise Sentiment Dashboard - Mac Setup
# ============================================

set -e

echo "🚀 Starting installation of all required software..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Homebrew is installed
if ! command -v brew &> /dev/null; then
    echo -e "${YELLOW}Installing Homebrew...${NC}"
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
else
    echo -e "${GREEN}✓ Homebrew already installed${NC}"
fi

# Update Homebrew
echo -e "${YELLOW}Updating Homebrew...${NC}"
brew update

# Install Java 17 (required for Flink)
echo -e "${YELLOW}Installing Java 17...${NC}"
if ! brew list openjdk@17 &> /dev/null; then
    brew install openjdk@17
    sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
else
    echo -e "${GREEN}✓ Java 17 already installed${NC}"
fi

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo "export JAVA_HOME=$JAVA_HOME" >> ~/.zshrc

# Install Maven
echo -e "${YELLOW}Installing Maven...${NC}"
if ! command -v mvn &> /dev/null; then
    brew install maven
else
    echo -e "${GREEN}✓ Maven already installed${NC}"
fi

# Install Kafka
echo -e "${YELLOW}Installing Apache Kafka...${NC}"
if ! brew list kafka &> /dev/null; then
    brew install kafka
else
    echo -e "${GREEN}✓ Kafka already installed${NC}"
fi

# Install Redis
echo -e "${YELLOW}Installing Redis...${NC}"
if ! brew list redis &> /dev/null; then
    brew install redis
else
    echo -e "${GREEN}✓ Redis already installed${NC}"
fi

# Install Node.js and npm
echo -e "${YELLOW}Installing Node.js...${NC}"
if ! command -v node &> /dev/null; then
    brew install node
else
    echo -e "${GREEN}✓ Node.js already installed${NC}"
fi

# Download Apache Flink 1.18.0
echo -e "${YELLOW}Downloading Apache Flink...${NC}"
FLINK_VERSION="1.18.0"
FLINK_DIR="$HOME/flink-${FLINK_VERSION}"

if [ ! -d "$FLINK_DIR" ]; then
    cd ~
    curl -O https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    tar -xzf flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    rm flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    echo -e "${GREEN}✓ Flink installed at ${FLINK_DIR}${NC}"
else
    echo -e "${GREEN}✓ Flink already installed${NC}"
fi

# Add Flink to PATH
echo "export FLINK_HOME=$FLINK_DIR" >> ~/.zshrc
echo "export PATH=\$PATH:\$FLINK_HOME/bin" >> ~/.zshrc

# Install jq for JSON parsing
echo -e "${YELLOW}Installing jq...${NC}"
if ! command -v jq &> /dev/null; then
    brew install jq
else
    echo -e "${GREEN}✓ jq already installed${NC}"
fi

# Create project directories
echo -e "${YELLOW}Creating project directories...${NC}"
mkdir -p ~/sentiment-dashboard/{kafka-producer,flink-jobs,websocket-server,react-dashboard,config,logs,data}

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✓ Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Installed versions:"
echo "  Java:  $(java -version 2>&1 | head -n 1)"
echo "  Maven: $(mvn -version | head -n 1)"
echo "  Kafka: $(brew list --versions kafka)"
echo "  Redis: $(redis-server --version)"
echo "  Node:  $(node --version)"
echo "  Flink: ${FLINK_VERSION}"
echo ""
echo "Project structure created at: ~/sentiment-dashboard/"
echo ""
echo "Next steps:"
echo "1. Source your shell configuration: source ~/.zshrc"
echo "2. Verify Java: java -version"
echo "3. Start services: ./start-services.sh"
echo ""
