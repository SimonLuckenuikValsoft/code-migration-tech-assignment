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
                echo "Updating package lists..."
                sudo apt-get update
                # Try Java versions in order of preference (11, 17, 21, 8)
                if apt-cache show openjdk-11-jdk &> /dev/null; then
                    sudo apt-get install -y openjdk-11-jdk
                elif apt-cache show openjdk-17-jdk &> /dev/null; then
                    echo "Java 11 not available, installing Java 17..."
                    sudo apt-get install -y openjdk-17-jdk
                elif apt-cache show openjdk-21-jdk &> /dev/null; then
                    echo "Java 11/17 not available, installing Java 21..."
                    sudo apt-get install -y openjdk-21-jdk
                elif apt-cache show openjdk-8-jdk &> /dev/null; then
                    echo "Installing Java 8 as fallback..."
                    sudo apt-get install -y openjdk-8-jdk
                else
                    echo "Error: No OpenJDK package found. Try running:"
                    echo "  sudo apt-get update"
                    echo "  sudo apt-cache search openjdk"
                    exit 1
                fi
            elif command -v yum &> /dev/null; then
                sudo yum install -y java-11-openjdk-devel || sudo yum install -y java-17-openjdk-devel
            elif command -v dnf &> /dev/null; then
                sudo dnf install -y java-11-openjdk-devel || sudo dnf install -y java-17-openjdk-devel
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
