package fr.mathias.proxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;

public class PacketUtils {

    public static ByteBuf createHandshakePacket(int protocolVersion, String host, int port, int nextState) {
        ByteBuf data = Unpooled.buffer();
        MinecraftEncoder.writeVarInt(data, protocolVersion);
        writeString(data, host);
        data.writeShort(port);
        MinecraftEncoder.writeVarInt(data, nextState);

        ByteBuf packet = Unpooled.buffer();
        MinecraftEncoder.writeVarInt(packet, data.readableBytes() + 1); // +1 pour l'ID
        MinecraftEncoder.writeVarInt(packet, 0x00); // ID Handshake
        packet.writeBytes(data);
        
        data.release();
        return packet;
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        MinecraftEncoder.writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}
