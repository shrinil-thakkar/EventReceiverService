#!/bin/bash

BUCKET_NAME="shrinil-portal26-events"  # Update if your bucket name changes in application.yml
LOG_FILE="/app/logs/event-receiver-service.log"
S3_PREFIX="logs"

while true; do
  if [ -f "$LOG_FILE" ]; then
    TIMESTAMP=$(date +%Y%m%d%H%M%S)
    S3_PATH="$S3_PREFIX/event-receiver-service-$TIMESTAMP.log"
    aws s3 cp "$LOG_FILE" "s3://$BUCKET_NAME/$S3_PATH"
  fi
  sleep 300  # 5 minutes

done 