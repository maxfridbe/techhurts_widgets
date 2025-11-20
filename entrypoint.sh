#!/bin/sh
# Use sh for better portability
set -e # Exit script on any error

# Define expected path and desired Gradle version
GRADLEW_PATH="/app/gradlew"
GRADLE_VERSION="8.7" # Should match the base image's Gradle version

# Check if gradlew exists in the /app directory (mounted volume)
if [ ! -f "$GRADLEW_PATH" ]; then
  echo "INFO: Gradle wrapper not found in /app. Generating..."
  # Navigate to the app directory and generate wrapper
  # This uses the 'gradle' command available in the base image
  cd /app && gradle wrapper --gradle-version "$GRADLE_VERSION"
  echo "INFO: Gradle wrapper generated in /app."
else
  echo "INFO: Using existing Gradle wrapper found in /app."
fi

# Now, execute the command passed to the container (the CMD or arguments from 'docker run')
echo "INFO: Executing command: $@"
exec "$@"
