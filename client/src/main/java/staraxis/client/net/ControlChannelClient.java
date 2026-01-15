package staraxis.client.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import staraxis.client.utils.Logger;
import staraxis.net.proto.ClientHello;
import staraxis.net.proto.ServerHello;
import staraxis.net.proto.ClientHeartbeat;
import staraxis.net.proto.ServerHeartbeatAck;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 控制通道客户端：负责握手与心跳。仅实现握手，心跳将在后续任务实现。
 */
public class ControlChannelClient {

    private final String host;
    private final int port;

    private Channel channel;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Gson gson = new Gson();

    public ControlChannelClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public CompletableFuture<ServerHello> connect() {
        CompletableFuture<ServerHello> handshakeFuture = new CompletableFuture<>();
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
                                Logger.info("ControlChannel connected, sending ClientHello");
                                ClientHello hello = new ClientHello("0.1.0");
                                byte[] payload = gson.toJson(hello).getBytes(StandardCharsets.UTF_8);
                                ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(payload));
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                String json = new String(msg, StandardCharsets.UTF_8);
                                ServerHello serverHello = gson.fromJson(json, ServerHello.class);
                                Logger.info("Received ServerHello clientId=" + serverHello.getClientId());
                                handshakeFuture.complete(serverHello);
                                channel = ctx.channel();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                Logger.error("ControlChannel error", cause);
                                handshakeFuture.completeExceptionally(cause);
                                ctx.close();
                            }
                        });
                    }
                });

        b.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                handshakeFuture.completeExceptionally(future.cause());
            }
        });
        return handshakeFuture;
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }
}
