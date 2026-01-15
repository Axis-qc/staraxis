package staraxis.server.net;

import com.google.gson.Gson;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.AttributeKey;
import staraxis.net.proto.ClientHeartbeat;
import staraxis.net.proto.ClientHello;
import staraxis.net.proto.ServerHeartbeatAck;
import staraxis.net.proto.ServerHello;
import staraxis.server.Session;
import staraxis.server.SessionManager;
import staraxis.server.utils.Logger;

import java.net.InetSocketAddress;

/**
 * 控制通道 Netty 服务器，负责握手与心跳。
 */
public class ControlChannelServer {

    private final int port;
    private final SessionManager sessionManager;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup worker = new NioEventLoopGroup();
    private Channel serverChannel;
    private static final Gson GSON = new Gson();

    private static final AttributeKey<String> ATTR_CLIENT_ID = AttributeKey.valueOf("clientId");

    public ControlChannelServer(int port, SessionManager sessionManager) {
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
                                if (json.contains("clientVersion")) { // 粗略区分消息类型
                                    ClientHello hello = GSON.fromJson(json, ClientHello.class);
                                    Logger.info("ClientHello version=" + hello.getClientVersion());
                                    String clientId = sessionManager.createSession(ctx.channel());
                                    ctx.channel().attr(ATTR_CLIENT_ID).set(clientId);
                                    ServerHello resp = new ServerHello(clientId, 0);
                                    ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(GSON.toJson(resp).getBytes()));
                                } else if (json.contains("clientSeq")) {
                                    ClientHeartbeat hb = GSON.fromJson(json, ClientHeartbeat.class);
                                    String cid = ctx.channel().attr(ATTR_CLIENT_ID).get();
                                    Session s = sessionManager.getSession(cid);
                                    if (s != null) {
                                        s.setLastHeartbeatAtMs(System.currentTimeMillis());
                                    }
                                    ServerHeartbeatAck ack = new ServerHeartbeatAck(hb.getClientSeq(), 0);
                                    ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(GSON.toJson(ack).getBytes()));
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                Logger.error("control channel handler error", cause);
                                ctx.close();
                            }
                        });
                    }
                });
        ChannelFuture f = b.bind(new InetSocketAddress("127.0.0.1", port)).sync();
        serverChannel = f.channel();
        Logger.info("ControlChannelServer listening on " + serverChannel.localAddress());
    }

    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }
}
