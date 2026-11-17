#!/bin/sh
#
# Gradle 启动脚本（标准模板，适用于 Gradle 8.x）
# 用法：./gradlew assembleRelease
#

# 解析 APP_HOME 为脚本所在目录
APP_HOME=$(cd "$(dirname "$0")" > /dev/null && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# 优先用 JAVA_HOME，否则用 PATH 里的 java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: 找不到 java。请设置 JAVA_HOME 或安装 JDK 17+。" >&2
    exit 1
fi

# JVM 参数（可由 GRADLE_OPTS / JAVA_OPTS 覆盖）
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname="gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
