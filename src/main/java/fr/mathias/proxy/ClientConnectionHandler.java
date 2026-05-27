package fr.mathias.proxy;

import fr.mathias.proxy.protocol.MinecraftDecoder;
import fr.mathias.proxy.protocol.MinecraftEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ClientConnectionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionHandler.class);

    private final String targetHost;
    private final int targetPort;
    private Channel outboundChannel;
    private final List<Object> pendingMessages = new ArrayList<>();
    private boolean connecting = false;

    public ClientConnectionHandler(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();
        LOGGER.info("Nouvelle connexion client: {}", inboundChannel.remoteAddress());

        connecting = true;
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
         .channel(inboundChannel.getClass())
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast("decoder", new MinecraftDecoder());
                 ch.pipeline().addLast("encoder", new MinecraftEncoder());
                 ch.pipeline().addLast("handler", new BackendConnectionHandler(inboundChannel));
             }
         })
         .option(ChannelOption.AUTO_READ, false);

        LOGGER.info("Connexion au serveur backend {}:{}...", targetHost, targetPort);
        ChannelFuture f = b.connect(targetHost, targetPort);
        outboundChannel = f.channel();
        
        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOGGER.info("Connecté au backend avec succès.");
                connecting = false;
                // Envoyer les messages en attente
                for (Object msg : pendingMessages) {
                    outboundChannel.writeAndFlush(msg);
                }
                pendingMessages.clear();
                inboundChannel.read();
            } else {
                LOGGER.error("Échec de connexion au backend: {}", future.cause().getMessage());
                for (Object msg : pendingMessages) {
                    ReferenceCountUtil.release(msg);
                }
                pendingMessages.clear();
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (outboundChannel != null && outboundChannel.isActive() && !connecting) {
            outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    LOGGER.error("Erreur d'écriture vers le backend.");
                    future.channel().close();
                }
            });
        } else if (connecting) {
            // Bufferiser le message pendant la connexion
            pendingMessages.add(msg);
        } else {
            LOGGER.warn("Message reçu mais pas de backend actif.");
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
        for (Object msg : pendingMessages) {
            ReferenceCountUtil.release(msg);
        }
        pendingMessages.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Client connection error", cause);
        closeOnFlush(ctx.channel());
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
