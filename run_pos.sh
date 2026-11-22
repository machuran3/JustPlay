#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

if [ -f "$DIR/build/release_assets/JustPlayPOS.jar" ]; then
    JAR_PATH="$DIR/build/release_assets/JustPlayPOS.jar"
elif [ -f "$DIR/JustPlay_POS_Desktop/build/libs/JustPlayPOS.jar" ]; then
    JAR_PATH="$DIR/JustPlay_POS_Desktop/build/libs/JustPlayPOS.jar"
else
    echo "No se encontró JustPlayPOS.jar"
    exit 1
fi

JAVA_CMD="java"
if command -v /usr/bin/java &> /dev/null; then
    JAVA_CMD="/usr/bin/java"
elif [ -f "/home/maru/.jdks/jbr-21.0.11/bin/java" ]; then
    JAVA_CMD="/home/maru/.jdks/jbr-21.0.11/bin/java"
fi

exec "$JAVA_CMD" -Xmx2048m -jar "$JAR_PATH" "$@"
