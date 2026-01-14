#!/bin/bash

set -e

echo "=========================================="
echo "Order Entry Application - Automated Setup"
echo "=========================================="
echo ""

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Check for reset flag
RESET_DB=0
if [[ "$1" == "--reset" || "$1" == "-r" ]]; then
    RESET_DB=1
fi

OS="$(uname -s)"
case "${OS}" in
    Linux*)     MACHINE=Linux;;
    Darwin*)    MACHINE=Mac;;
    *)          MACHINE=Linux;;
esac

echo "Detected OS: ${MACHINE}"
echo ""

if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    echo "Java found: ${JAVA_VERSION}"
else
    echo "Java not found. Installing OpenJDK 11..."
    
    case "${MACHINE}" in
        Linux)
            if command -v apt-get &> /dev/null; then
                sudo apt-get update -qq
                sudo apt-get install -y -qq openjdk-11-jdk
            elif command -v yum &> /dev/null; then
                sudo yum install -y -q java-11-openjdk-devel
            elif command -v dnf &> /dev/null; then
                sudo dnf install -y -q java-11-openjdk-devel
            else
                echo "Error: Could not detect package manager"
                exit 1
            fi
            ;;
        
        Mac)
            if command -v brew &> /dev/null; then
                brew install --quiet openjdk@11
                sudo ln -sfn /usr/local/opt/openjdk@11/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-11.jdk
            else
                echo "Error: Homebrew not found. Install from https://brew.sh"
                exit 1
            fi
            ;;
    esac
    
    echo "Java installed successfully"
fi

echo ""
echo "Downloading dependencies and building application..."
echo ""

./mvnw clean compile -q

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Delete database if reset flag was provided
if [ $RESET_DB -eq 1 ]; then
    echo ""
    echo "Resetting database..."
    rm -f orderentry.db
    echo "Database reset complete."
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Starting application..."
echo "(Use \"./run.sh --reset\" to reset the database)"
echo ""

./mvnw -q exec:java
