#!/bin/bash
# Session-based Login Test Script
# Usage: ./test-session-login.sh <session-id> <character-id>

set -e

SESSION_ID="${1}"
CHARACTER_ID="${2}"
JAR_PATH="${3:-runelite-client/build/libs/client-1.0.0-SNAPSHOT-shaded.jar}"

if [ -z "$SESSION_ID" ] || [ -z "$CHARACTER_ID" ]; then
    echo "Usage: $0 <session-id> <character-id> [jar-path]"
    echo ""
    echo "Example:"
    echo "  $0 'abc123def456...' '12345'"
    echo ""
    echo "Or use environment variables:"
    echo "  export SESSION_ID='abc123def456...'"
    echo "  export CHARACTER_ID='12345'"
    echo "  $0 \$SESSION_ID \$CHARACTER_ID"
    exit 1
fi

echo "========================================"
echo "Session-based Login Test"
echo "========================================"
echo ""

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR file not found at: $JAR_PATH"
    echo "Building the client first..."
    ./gradlew :client:assemble
fi

# Mask session ID for display (show first 8 chars only)
MASKED_SESSION_ID="${SESSION_ID:0:8}***"

echo "Configuration:"
echo "  Session ID: $MASKED_SESSION_ID"
echo "  Character ID: $CHARACTER_ID"
echo "  JAR Path: $JAR_PATH"
echo ""

echo "Starting client with session login..."
echo ""

# Launch the client
java -jar "$JAR_PATH" \
    --session-id "$SESSION_ID" \
    --character-id "$CHARACTER_ID" \
    --debug

echo ""
echo "Client exited."
