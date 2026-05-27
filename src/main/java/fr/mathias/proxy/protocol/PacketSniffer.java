package fr.mathias.proxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public class PacketSniffer extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) return;

        in.markReaderIndex();
        int length = readVarInt(in);
        if (length <= 0 || in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        // On garde le paquet entier (avec sa taille) pour le transmettre tel quel
        in.resetReaderIndex();
        out.add(in.readRetainedSlice(length + getVarIntLength(length)));
    }

    private int readVarInt(ByteBuf buf) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            if (buf.readableBytes() == 0) return -1;
            read = buf.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) return -1;
        } while ((read & 0b10000000) != 0);
        return result;
    }

    private int getVarIntLength(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) return 1;
        if ((value & (0xFFFFFFFF << 14)) == 0) return 2;
        if ((value & (0xFFFFFFFF << 21)) == 0) return 3;
        if ((value & (0xFFFFFFFF << 28)) == 0) return 4;
        return 5;
    }
}
