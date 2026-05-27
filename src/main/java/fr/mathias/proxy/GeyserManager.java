package fr.mathias.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GeyserManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeyserManager.class);
    private static final String GEYSER_URL = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone";
    private static final String GEYSER_FILE = "Geyser-Standalone.jar";

    public static void setup() {
        Path path = Paths.get(GEYSER_FILE);
        if (!Files.exists(path)) {
            LOGGER.info("Geyser-Standalone.jar non trouvé. Téléchargement de la dernière version...");
            downloadGeyser(path);
        } else {
            LOGGER.info("Geyser-Standalone.jar déjà présent.");
        }
        
        createOptimizedConfig();
    }

    private static void downloadGeyser(Path path) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(GEYSER_URL).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(path.toFile())) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            LOGGER.info("Téléchargement de Geyser terminé !");
        } catch (IOException e) {
            LOGGER.error("Erreur lors du téléchargement de Geyser: {}", e.getMessage());
        }
    }

    private static void createOptimizedConfig() {
        // TODO: Générer un fichier config.yml pour Geyser si absent
        // On forcera le remote port sur 25566 (notre proxy)
        LOGGER.info("Configuration de Geyser pour rediriger vers le port 25566...");
    }
}
