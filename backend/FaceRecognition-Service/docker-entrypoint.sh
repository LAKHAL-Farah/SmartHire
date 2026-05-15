#!/bin/bash

set -e

echo "=================================================="
echo "Face Recognition Service - Docker Entrypoint"
echo "=================================================="

# Load environment variables
if [ -f .env ]; then
    echo "ℹ️  Loading environment from .env file"
    export $(cat .env | grep -v '#' | xargs)
fi

# Display configuration
echo "Configuration:"
echo "  - Service: Face Recognition API"
echo "  - Host: ${HOST:-0.0.0.0}"
echo "  - Port: ${PORT:-5000}"
echo "  - Model: ${FACE_RECOGNITION_MODEL:-hog}"
echo "  - Confidence Threshold: ${CONFIDENCE_THRESHOLD:-0.85}"
echo "  - Environment: ${FLASK_ENV:-production}"

# Health check
echo ""
echo "Running initial health check..."
sleep 3

# Execute the command passed to docker
echo ""
echo "Starting service..."
exec "$@"
