# ──────────────────────────────────────────────────────
# SearCam Android Build Environment
#
# JDK 17 + Android SDK 34 + Gradle 8.2
# Supports: unit tests (no device), debug/release APK build
# ──────────────────────────────────────────────────────

FROM eclipse-temurin:17-jdk-jammy

# ── System deps ──
RUN apt-get update && apt-get install -y \
    curl unzip wget git \
    libgl1 libglu1-mesa \
    && rm -rf /var/lib/apt/lists/*

# ── Android SDK ──
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
         -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools && \
    mv /tmp/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# ── Accept licenses & install SDK components ──
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0"

# ── Gradle wrapper (cached layer) ──
ENV GRADLE_VERSION=8.2
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
         -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

WORKDIR /project

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["test"]
