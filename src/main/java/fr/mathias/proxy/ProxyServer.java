package fr.mathias.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);
    
    // To achieve 50MB max memory, we drastically reduce Netty's thread count
    // 1 boss thread, 1 worker thread is enough for 2 players.
    private static final int BOSS_THREADS = 1;
    private static final int WORKER_THREADS = 1;
    private static final int PORT = 25566;

    private static void startMemoryMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (true) {
                try {
                    Runtime runtime = Runtime.getRuntime();
                    long maxMemory = runtime.maxMemory() / 1024 / 1024;
                    long allocatedMemory = runtime.totalMemory() / 1024 / 1024;
                    long freeMemory = runtime.freeMemory() / 1024 / 1024;
                    long usedMemory = allocatedMemory - freeMemory;

                    LOGGER.info("--- MONITORING RAM ---");
                    LOGGER.info("Utilisée: {} Mo | Allouée: {} Mo | Max: {} Mo", usedMemory, allocatedMemory, maxMemory);
                    LOGGER.info("----------------------");

                    Thread.sleep(10000); // Toutes les 10 secondes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "RAM-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public static void main(String[] args) throws Exception {
        LOGGER.info("Démarrage de UltraProxy...");
        
        // Démarrage du monitoring RAM (toutes les 10 secondes)
        startMemoryMonitoring();
        
        // Plugin Loading would go here
        PluginManager.init();
        
        // Setup Geyser
        GeyserManager.setup();

        boolean useEpoll = Epoll.isAvailable();
        EventLoopGroup bossGroup = useEpoll ? new EpollEventLoopGroup(BOSS_THREADS) : new NioEventLoopGroup(BOSS_THREADS);
        EventLoopGroup workerGroup = useEpoll ? new EpollEventLoopGroup(WORKER_THREADS) : new NioEventLoopGroup(WORKER_THREADS);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(useEpoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
             .childHandler(new ProxyInitializer())
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             // Optimization: Disable Nagle's algorithm for low latency
             .childOption(ChannelOption.TCP_NODELAY, true)
             // Optimization: Pooled byte buffers for low GC overhead
             .childOption(ChannelOption.ALLOCATOR, io.netty.buffer.PooledByteBufAllocator.DEFAULT);

            LOGGER.info("Proxy en écoute sur le port {} (Epoll: {})", PORT, useEpoll);
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
