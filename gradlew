#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -r "$CLASSPATH" ]; then
    echo "Gradle wrapper JAR is missing: $CLASSPATH" >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVACMD=java
else
    echo "No Java runtime found. Install OpenJDK 17/21 or point JAVA_HOME to an Android Studio JDK." >&2
    exit 1
fi

exec "$JAVACMD" \
    -Xmx64m \
    -Xms64m \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
