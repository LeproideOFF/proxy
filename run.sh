#!/bin/bash
# UltraProxy & Geyser Launcher - 128MB RAM Profile

JAVA_BIN="/opt/homebrew/opt/openjdk@21/bin/java"

echo "--- Nettoyage des ports ---"
lsof -ti :25566 | xargs kill -9 2>/dev/null
lsof -ti :19132 | xargs kill -9 2>/dev/null
sleep 1

echo "--- Préparation du lancement (Budget RAM: 128MB) ---"

# 1. Lancement du Proxy Ultra-Optimisé (~40MB RAM)
echo "[1/2] Démarrage de UltraProxy..."
$JAVA_BIN -Xms32M -Xmx48M -XX:+UseSerialGC -XX:MaxDirectMemorySize=12M \
     -jar build/libs/ultra-proxy-1.0-SNAPSHOT-all.jar &
PROXY_PID=$!

sleep 3

# 2. Lancement de Geyser Standalone (~80MB RAM)
if [ -f "Geyser-Standalone.jar" ]; then
    echo "[2/2] Démarrage de GeyserMC (Support Bedrock)..."
    # Geyser a besoin d'au moins 64-80MB pour charger ses registres de blocs
    $JAVA_BIN -Xms64M -Xmx80M -XX:+UseSerialGC \
         -jar Geyser-Standalone.jar --nogui &
    GEYSER_PID=$!
else
    echo "[!] Geyser-Standalone.jar introuvable."
fi

echo "--- Systèmes démarrés ! ---"
wait $PROXY_PID $GEYSER_PID
