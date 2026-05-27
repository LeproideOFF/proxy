package fr.mathias.proxy;

import fr.mathias.proxy.api.ProxyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);
    private static final List<ProxyPlugin> PLUGINS = new ArrayList<>();
    private static final File PLUGIN_DIR = new File("plugins");

    public static void init() {
        if (!PLUGIN_DIR.exists()) {
            PLUGIN_DIR.mkdirs();
        }

        File[] files = PLUGIN_DIR.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            LOGGER.info("Aucun plugin trouvé dans le dossier 'plugins/'.");
            return;
        }

        for (File file : files) {
            loadPlugin(file);
        }

        PLUGINS.forEach(ProxyPlugin::onEnable);
    }

    private static void loadPlugin(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("plugin.properties");
            if (entry == null) {
                LOGGER.warn("Le plugin {} n'a pas de fichier plugin.properties !", file.getName());
                return;
            }

            Properties props = new Properties();
            try (InputStream is = jar.getInputStream(entry)) {
                props.load(is);
            }

            String mainClass = props.getProperty("main");
            URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL()}, PluginManager.class.getClassLoader());
            Class<?> clazz = Class.forName(mainClass, true, loader);
            
            if (ProxyPlugin.class.isAssignableFrom(clazz)) {
                ProxyPlugin plugin = (ProxyPlugin) clazz.getDeclaredConstructor().newInstance();
                PLUGINS.add(plugin);
                LOGGER.info("Plugin chargé : {}", file.getName());
            }

        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement du plugin {}: {}", file.getName(), e.getMessage());
        }
    }

    public static void stop() {
        PLUGINS.forEach(ProxyPlugin::onDisable);
    }
}
