package fr.mathias.proxy.config;

import java.util.HashMap;
import java.util.Map;

public class ProxyConfig {
    public static final Map<String, BackendServer> SERVERS = new HashMap<>();
    public static String DEFAULT_SERVER = "lobby";

    static {
        // Configuration par défaut
        SERVERS.put("lobby", new BackendServer("127.0.0.1", 25565));
        SERVERS.put("leproide", new BackendServer("leproide.aternos.me", 49881));
    }

    public record BackendServer(String host, int port) {}
}
