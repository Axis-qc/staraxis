package staraxis.server.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * Netty pipeline initializer for the control channel. Only framing handlers are configured here.
 * Actual message codec / business logic handlers will be added in later tasks.
 */
public class ControlChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_FRAME_LENGTH = 65_535;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        // Inbound: frame decoder (length field at 0, 4 bytes, big-endian)
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                MAX_FRAME_LENGTH, 0, 4, 0, 4));
        // Outbound: prepend length field
        p.addLast("frameEncoder", new LengthFieldPrepender(4));
        // TODO: add codec (JSON / Protobuf) and business handlers in later tasks
    }
}
