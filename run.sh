#!/bin/bash
echo "Compilation en cours..."
./gradlew shadowJar --no-daemon

# Utilisation du chemin spécifique pour OpenJDK 21 installé via Homebrew
JAVA_BIN="/opt/homebrew/opt/openjdk@21/bin/java"

echo "Démarrage du proxy avec profil mémoire ultra-agressif (Max 50MB)..."
$JAVA_BIN -Xms32M -Xmx32M -XX:+UseSerialGC -XX:MaxDirectMemorySize=16M \
     -Dio.netty.maxDirectMemory=16777216 \
     -Dio.netty.allocator.type=pooled \
     -jar build/libs/ultra-proxy-1.0-SNAPSHOT-all.jar
