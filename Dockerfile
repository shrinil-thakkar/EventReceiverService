# Use OpenJDK 17 as the base image
FROM maven:3.9.6-eclipse-temurin-17

# Set working directory
WORKDIR /app

# Copy the entire project
COPY . .

# Build the application
RUN mvn package -DskipTests

# Install AWS CLI for log uploads
RUN apt-get update && apt-get install -y awscli

# Copy the log upload script
COPY upload-logs.sh /app/upload-logs.sh
RUN chmod +x /app/upload-logs.sh

# Ensure logs directory exists for file logging
RUN mkdir -p /app/logs

# Expose the application port
EXPOSE 8080

# Run the log uploader in the background and start the app
ENTRYPOINT ["sh", "-c", "/app/upload-logs.sh & java -jar target/*.jar"] 