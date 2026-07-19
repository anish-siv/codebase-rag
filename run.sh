#!/usr/bin/env bash
# Sources .env (never prints it) and boots the Spring Boot app.
# Requires JDK 21; if a JDK 21 install is found at a common macOS path and
# your default `java` isn't already 21, JAVA_HOME is pointed at it.
set -euo pipefail
cd "$(dirname "$0")"

set -a
source .env
set +a

if ! java -version 2>&1 | head -1 | grep -q '"21'; then
  for candidate in \
    /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
    /usr/local/opt/openjdk@21 \
    /opt/homebrew/opt/openjdk@21; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

exec ./mvnw spring-boot:run
