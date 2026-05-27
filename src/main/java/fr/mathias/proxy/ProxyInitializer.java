package fr.mathias.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ProxyInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final String TARGET_HOST = "127.0.0.1";
    private static final int TARGET_PORT = 25566;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // TODO: For a full proxy, we would add VarInt frame decoders here.
        // For the absolute lowest memory footprint (50MB) when we just want to forward,
        // we can just pipe the bytes directly to the backend server.
        // However, to support plugins like Geyser, we need to decode packets.
        
        pipeline.addLast(new ClientConnectionHandler(TARGET_HOST, TARGET_PORT));
    }
}
