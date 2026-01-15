package staraxis.client.net;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import staraxis.client.utils.Logger;
import staraxis.net.proto.DataHello;
import staraxis.net.proto.ServerTick;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DataChannelClient {

    private final String host;
    private final int port;
    private final String clientId;

    private final Gson gson = new Gson();
    private final EventLoopGroup group = new NioEventLoopGroup(1);

    public DataChannelClient(String host, int port, String clientId) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
    }

    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LengthFieldBasedFrameDecoder(65_535, 0, 4, 0, 4));
                        p.addLast(new LengthFieldPrepender(4));
                        p.addLast(new SimpleChannelInboundHandler<byte[]>() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                Logger.info("DataChannel connected, sending DataHello");
                                DataHello hello = new DataHello(clientId);
                                byte[] payload = gson.toJson(hello).getBytes(StandardCharsets.UTF_8);
                                ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(payload));
                                ready.complete(null);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                String json = new String(msg, StandardCharsets.UTF_8);
                                ServerTick tick = gson.fromJson(json, ServerTick.class);
                                Logger.info("Received ServerTick=" + tick.getServerTick());
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                Logger.error("DataChannel error", cause);
                                ctx.close();
                            }
                        });
                    }
                });
        b.connect(host, port);
        return ready;
    }

    public void shutdown() {
        group.shutdownGracefully();
    }
}
