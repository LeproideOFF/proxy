package fr.mathias.proxy;

import fr.mathias.proxy.protocol.PacketSniffer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ProxyInitializer extends ChannelInitializer<SocketChannel> {
    
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // Packet Sniffer : Isole les paquets sans les décoder entièrement
        pipeline.addLast("sniffer", new PacketSniffer());
        pipeline.addLast("handler", new ClientConnectionHandler());
    }
}
