#!/usr/bin/env bash
# Sources .env (never prints it) and boots the Spring Boot app.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
exec ./mvnw spring-boot:run
