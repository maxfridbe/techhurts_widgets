# Use a base image that includes a specific version of Gradle and JDK 17
FROM gradle:8.7.0-jdk17

# Set environment variables for Android SDK
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH $PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Install Android SDK prerequisites and tools
RUN apt-get update && \
    apt-get install -y wget unzip --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

ARG CMDLINE_TOOLS_VERSION="11076708" # Example version, update if needed
RUN wget "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O cmdline-tools.zip && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    unzip cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm cmdline-tools.zip

RUN yes | ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --licenses
RUN ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0"

# Set working directory (commands in CMD/ENTRYPOINT run relative to this unless changed)
WORKDIR /app

# --- NEW: Copy and set up the entrypoint script ---
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
# --- End of new entrypoint setup ---

# Define the default command to pass TO the entrypoint script
CMD ["./gradlew", "assembleDebug", "--no-daemon"]

# --- End of Dockerfile ---
