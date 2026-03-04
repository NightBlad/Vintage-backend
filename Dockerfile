# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY mvnw.cmd .
COPY pom.xml .
COPY .mvn .mvn

# Copy source code
COPY src src

# Make mvnw executable
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

# Create data directory for H2 database
RUN mkdir -p /app/data

# Set permissions for /app/data to be writable by any user (for H2 database)
RUN chmod -R 777 /app/data

# Expose port
EXPOSE 1199

# Run the application
CMD ["java", "-jar", "target/vintage-0.0.1-SNAPSHOT.jar"]
