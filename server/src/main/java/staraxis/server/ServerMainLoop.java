package staraxis.server;

import staraxis.server.utils.Logger;

/**
 * 服务端权威主循环（25 tick/s）。
 * 按 Prepare → Update → Commit → PostUpdate 四阶段推进。
 */
public class ServerMainLoop implements Runnable {

    private final TickState tickState = new TickState();
    private final staraxis.server.SessionManager sessionManager;

    public ServerMainLoop(staraxis.server.SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /** 目标间隔：纳秒 */
    private static final long FRAME_NANOS = 1_000_000_000L / TickState.TICKS_PER_SECOND;

    private volatile boolean running = true;

    @Override
    public void run() {
        Logger.info("ServerMainLoop started on thread=" + Thread.currentThread().getName());
        while (running && !Thread.currentThread().isInterrupted()) {
            long frameStart = System.nanoTime();

            // ==== PrepareTick ====
            prepareTick();
            // ==== Update ====
            update();
            // ==== Commit ====
            commit();
            // ==== PostUpdate ====
            postUpdate();

            // ==== 节拍控制 ====
            long frameCost = System.nanoTime() - frameStart;
            long sleepNanos = FRAME_NANOS - frameCost;
            if (sleepNanos > 0) {
                try {
                    // nanosleep 精度有限，先转毫秒
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 输出 tickCost 观测
            Logger.info("tick=" + tickState.getServerTick() + " costMs=" + (frameCost / 1_000_000.0));
        }
    }

    public void shutdown() {
        this.running = false;
    }

    private void prepareTick() {
        tickState.advanceTick();
    }

    private void update() {
        // TODO: 游戏系统更新将在后续用户故事实现
    }

    private void commit() {
        // TODO: 权威写入阶段占位
    }

    private void postUpdate() {
        // 下发 ServerTick 给所有已绑定数据通道
        long tick = tickState.getServerTick();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        byte[] payload = gson.toJson(new staraxis.net.proto.ServerTick(tick)).getBytes();
        for (staraxis.server.Session s : sessionManager.allSessions()) {
            if (s.getDataChannel() != null && s.getDataChannel().isActive()) {
                s.getDataChannel().writeAndFlush(s.getDataChannel().alloc().buffer().writeBytes(payload));
                s.setLastServerTickSent(tick);
            }
        }
    }
}
