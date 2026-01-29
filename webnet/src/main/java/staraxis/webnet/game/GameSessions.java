package staraxis.webnet.game;

import staraxis.game.StarAxisGameRuntime;

/**
 * GameSessions
 *
 * 单世界运行时存储（webnet 进程内）。
 *
 * 约束：
 * - 当前阶段：一次只能创建一个世界（global runtime）。
 * - 未来：如需多开世界，再扩展为 gameSessionId -> runtime。
 */
public final class GameSessions {

    private static volatile StarAxisGameRuntime runtime;

    private GameSessions() {
    }

    public static void setRuntime(StarAxisGameRuntime r) {
        runtime = r;
    }

    public static StarAxisGameRuntime getRuntime() {
        return runtime;
    }

    public static boolean hasRuntime() {
        return runtime != null;
    }
}
