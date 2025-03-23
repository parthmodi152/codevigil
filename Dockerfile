FROM clojure:openjdk-17-tools-deps-slim-buster

WORKDIR /app

# Install leiningen
RUN apt-get update && \
    apt-get install -y curl && \
    curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/local/bin && \
    lein

# Copy project files
COPY project.clj /app/
COPY src /app/src/
COPY resources /app/resources/

# Download dependencies
RUN lein deps

# Build uberjar
RUN lein uberjar

# Expose the API port
EXPOSE 3000

# Set environment variables
ENV PORT=3000
ENV DB_HOST=postgres
ENV DB_PORT=5432
ENV DB_NAME=codevigil
ENV DB_USER=postgres
# Note: DB_PASSWORD should be provided at runtime

# Run the application
CMD ["java", "-jar", "target/uberjar/codevigil-0.1.0-SNAPSHOT-standalone.jar"]
