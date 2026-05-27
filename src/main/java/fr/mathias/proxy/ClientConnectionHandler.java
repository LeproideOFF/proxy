package fr.mathias.proxy;

import fr.mathias.proxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ClientConnectionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionHandler.class);
    private static final Map<String, String> PENDING_REDIRECTS = new HashMap<>();

    private Channel outboundChannel;
    private String remoteAddress;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String fullAddress = ctx.channel().remoteAddress().toString();
        // Nettoyage de l'IP (ex: /127.0.0.1:12345 -> 127.0.0.1)
        this.remoteAddress = fullAddress.split(":")[0].replace("/", "");
        
        String target = PENDING_REDIRECTS.getOrDefault(remoteAddress, ProxyConfig.DEFAULT_SERVER);
        LOGGER.info("Client {} connecté. Direction : {}", remoteAddress, target);
        connectTo(ctx, target);
    }

    public void connectTo(ChannelHandlerContext ctx, String serverName) {
        ProxyConfig.BackendServer server = ProxyConfig.SERVERS.get(serverName);
        if (server == null) {
            ctx.close();
            return;
        }

        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
         .channel(ctx.channel().getClass())
         .handler(new BackendConnectionHandler(ctx.channel()))
         .option(ChannelOption.AUTO_READ, true);

        b.connect(server.host(), server.port()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                LOGGER.info("Tunnel établi vers {}", serverName);
            } else {
                LOGGER.error("Impossible de joindre {}", serverName);
                ctx.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            // Scan d'octets robuste pour "server"
            buf.markReaderIndex();
            byte[] pattern = "server".getBytes(StandardCharsets.UTF_8);
            int matchIndex = -1;
            for (int i = 0; i <= buf.readableBytes() - pattern.length; i++) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (buf.getByte(buf.readerIndex() + i + j) != pattern[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) { matchIndex = i; break; }
            }

            if (matchIndex != -1) {
                buf.skipBytes(matchIndex);
                String content = buf.toString(StandardCharsets.UTF_8);
                LOGGER.info("Commande détectée : {}", content);
                
                String[] parts = content.split(" ");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].contains("server") && i + 1 < parts.length) {
                        String target = parts[i+1].replaceAll("[^a-zA-Z0-9.-]", "");
                        if (ProxyConfig.SERVERS.containsKey(target)) {
                            PENDING_REDIRECTS.put(remoteAddress, target);
                            LOGGER.info("REDirection mémorisée pour {} -> {}", remoteAddress, target);
                            
                            // Message de déco propre
                            String kickMsg = "§aRedirection vers " + target + "...\n§7Relance ta connexion pour confirmer !";
                            ctx.writeAndFlush(Unpooled.copiedBuffer(kickMsg, StandardCharsets.UTF_8))
                               .addListener(ChannelFutureListener.CLOSE);
                            
                            ReferenceCountUtil.release(msg);
                            return;
                        }
                    }
                }
            }
            buf.resetReaderIndex();
        }

        if (outboundChannel != null && outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) outboundChannel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
