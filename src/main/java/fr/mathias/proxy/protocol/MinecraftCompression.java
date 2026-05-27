package fr.mathias.proxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class MinecraftCompression {

    public static class Decoder extends ByteToMessageDecoder {
        private final Inflater inflater = new Inflater();
        private final int threshold;

        public Decoder(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (!in.isReadable()) return;

            in.markReaderIndex();
            int packetLength = MinecraftDecoder.readVarInt(in);
            if (in.readableBytes() < packetLength) {
                in.resetReaderIndex();
                return;
            }

            int dataLength = MinecraftDecoder.readVarInt(in);
            if (dataLength == 0) {
                // Pas compressé
                out.add(in.readRetainedSlice(packetLength - getVarIntSize(0)));
            } else {
                // Compressé
                if (dataLength < threshold) {
                    throw new RuntimeException("Paquet compressé plus petit que le seuil !");
                }
                byte[] compressed = new byte[in.readableBytes()];
                in.readBytes(compressed);
                inflater.setInput(compressed);

                byte[] decompressed = new byte[dataLength];
                inflater.inflate(decompressed);
                inflater.reset();

                out.add(Unpooled.wrappedBuffer(decompressed));
            }
        }
    }

    public static class Encoder extends MessageToByteEncoder<ByteBuf> {
        private final Deflater deflater = new Deflater();
        private final int threshold;

        public Encoder(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            int uncompressedLength = msg.readableBytes();
            if (uncompressedLength < threshold) {
                // Trop petit, on n'ajoute pas de compression
                MinecraftEncoder.writeVarInt(out, uncompressedLength + getVarIntSize(0));
                MinecraftEncoder.writeVarInt(out, 0);
                out.writeBytes(msg);
            } else {
                // Compression
                byte[] uncompressed = new byte[uncompressedLength];
                msg.readBytes(uncompressed);
                deflater.setInput(uncompressed);
                deflater.finish();

                byte[] buffer = new byte[8192];
                ByteBuf compressedData = Unpooled.buffer();
                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    compressedData.writeBytes(buffer, 0, count);
                }
                deflater.reset();

                MinecraftEncoder.writeVarInt(out, compressedData.readableBytes() + getVarIntSize(uncompressedLength));
                MinecraftEncoder.writeVarInt(out, uncompressedLength);
                out.writeBytes(compressedData);
                compressedData.release();
            }
        }
    }

    private static int getVarIntSize(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) return 1;
        if ((value & (0xFFFFFFFF << 14)) == 0) return 2;
        if ((value & (0xFFFFFFFF << 21)) == 0) return 3;
        if ((value & (0xFFFFFFFF << 28)) == 0) return 4;
        return 5;
    }
}
