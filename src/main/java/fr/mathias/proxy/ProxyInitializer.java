package fr.mathias.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ProxyInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final String TARGET_HOST = "127.0.0.1";
    private static final int TARGET_PORT = 25565;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // Mode RAW TUNNEL pour Geyser : On ne touche pas aux paquets
        // On retire les décodeurs/encodeurs Minecraft qui peuvent corrompre 
        // les paquets traduits par Geyser.
        
        pipeline.addLast("handler", new ClientConnectionHandler(TARGET_HOST, TARGET_PORT));
    }
}
