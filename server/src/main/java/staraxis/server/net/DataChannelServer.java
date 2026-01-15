package staraxis.server.net;

import com.google.gson.Gson;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import staraxis.net.proto.DataHello;
import staraxis.server.Session;
import staraxis.server.SessionManager;
import staraxis.server.utils.Logger;

import java.net.InetSocketAddress;

/**
 * 数据通道服务器：绑定 clientId 并保存 Channel。
 */
public class DataChannelServer {

    private final int port;
    private final SessionManager sessionManager;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup worker = new NioEventLoopGroup();
    private Channel serverChannel;
    private static final Gson GSON = new Gson();

    public DataChannelServer(int port, SessionManager sessionManager) {
        this.port = port;
        this.sessionManager = sessionManager;
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LengthFieldBasedFrameDecoder(65_535, 0, 4, 0, 4));
                        p.addLast(new LengthFieldPrepender(4));
                        p.addLast(new SimpleChannelInboundHandler<byte[]>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                String json = new String(msg);
                                DataHello hello = GSON.fromJson(json, DataHello.class);
                                Session s = sessionManager.getSession(hello.getClientId());
                                if (s == null) {
                                    Logger.error("DataChannel bind failed, unknown clientId=" + hello.getClientId(),
                                            null);
                                    ctx.close();
                                } else {
                                    sessionManager.bindDataChannel(hello.getClientId(), ctx.channel());
                                    Logger.info("DataChannel bound for clientId=" + hello.getClientId());
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                Logger.error("data channel error", cause);
                                ctx.close();
                            }
                        });
                    }
                });
        ChannelFuture f = b.bind(new InetSocketAddress("127.0.0.1", port)).sync();
        serverChannel = f.channel();
        Logger.info("DataChannelServer listening on " + serverChannel.localAddress());
    }

    public void shutdown() {
        if (serverChannel != null)
            serverChannel.close();
        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }
}
