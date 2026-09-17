# Builder image for every widget module; the host never needs a JDK or the Android SDK.
FROM gradle:8.7.0-jdk17

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN apt-get update && \
    apt-get install -y wget unzip --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# https://developer.android.com/studio#command-tools
ARG CMDLINE_TOOLS_VERSION="11076708"
RUN wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O cmdline-tools.zip && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null

ARG ANDROID_PLATFORM="android-34"
ARG BUILD_TOOLS="34.0.0"
RUN sdkmanager \
    "platform-tools" \
    "platforms;${ANDROID_PLATFORM}" \
    "build-tools;${BUILD_TOOLS}"

WORKDIR /app
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["./gradlew", "assembleDebug", "--no-daemon"]
