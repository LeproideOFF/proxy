package fr.mathias.proxy;

import fr.mathias.proxy.protocol.MinecraftDecoder;
import fr.mathias.proxy.protocol.MinecraftEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ProxyInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final String TARGET_HOST = "127.0.0.1";
    private static final int TARGET_PORT = 25565;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // Protocol handling
        pipeline.addLast("decoder", new MinecraftDecoder());
        pipeline.addLast("encoder", new MinecraftEncoder());
        
        // Connection handling
        pipeline.addLast("handler", new ClientConnectionHandler(TARGET_HOST, TARGET_PORT));
    }
}
