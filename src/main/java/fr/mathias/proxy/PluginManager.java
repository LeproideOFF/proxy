package fr.mathias.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    public static void init() {
        LOGGER.info("Initialisation du système de plugins (faible empreinte mémoire)...");
        // TODO: Scan un dossier 'plugins/', charger les .jar via URLClassLoader
        // Pour limiter la RAM (<50MB), on utilisera un ClassLoader très simple
        // qui ne charge en mémoire que ce qui est strictement nécessaire.
    }
}
