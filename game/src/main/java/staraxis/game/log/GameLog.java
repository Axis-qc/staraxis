package staraxis.game.log;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameLog 喵。
 *
 * 作用喵：
 * - game 模块唯一权威日志出口喵！
 *
 * 约束喵：
 * - 日志写入 gamedata/logs/game.log 喵！
 * - 进程启动时调用 initTruncate() 清空旧文件喵！
 * - 仅供 game 模块内逻辑调用喵！
 */
public final class GameLog {

    private static final Path LOG_PATH = Path.of("gamedata/logs/game.log");

    private static volatile PrintWriter out;

    private static volatile boolean inited;

    /** 可读时间格式化器喵。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    /**
     * 将毫秒时间戳格式化为可读时间喵。
     */
    private static String fmtTs(long ms) {
        return TS.format(Instant.ofEpochMilli(ms));
    }

    /** 频率限制用：key -> 上次打印时间戳（毫秒）喵。 */
    private static final Map<String, Long> lastLogTimeByKey = new ConcurrentHashMap<>();

    /** 同一 key 的最小间隔（毫秒），默认 60 秒喵。 */
    private static final long THROTTLE_INTERVAL_MS = 60_000L;

    private GameLog() {
    }

    /**
     * 初始化并截断日志文件喵。
     * 仅在进程启动或首次进入模拟层时调用一次喵。
     */
    public static synchronized void initTruncate() {
        if (inited) {
            return;
        }
        inited = true;

        try {
            Files.createDirectories(LOG_PATH.getParent());
            out = new PrintWriter(new FileWriter(LOG_PATH.toFile(), false), true);
            out.println("[game.log] init (truncate) at " + fmtTs(System.currentTimeMillis()) + " 喵");
        } catch (Exception e) {
            out = null;
        }
    }

    /**
     * 记录普通日志消息喵。
     *
     * @param msg 消息内容喵
     */
    public static void log(String msg) {
        PrintWriter w = out;
        if (w == null) {
            // 如果未初始化，回退到标准输出喵
            System.out.println("[GameLog-Fallback] " + msg + " 喵");
            return;
        }
        w.println("[" + fmtTs(System.currentTimeMillis()) + "] " + msg + " 喵");
    }

    /**
     * 按 key 限流的日志打印喵：同一 key 在 60 秒内最多打印一次喵。
     *
     * @param key 业务 key 喵
     * @param msg 日志内容喵
     */
    public static void logThrottled(String key, String msg) {
        long now = System.currentTimeMillis();
        Long last = lastLogTimeByKey.get(key);
        if (last != null && now - last < THROTTLE_INTERVAL_MS) {
            return;
        }
        lastLogTimeByKey.put(key, now);
        log(msg);
    }

    /**
     * 记录错误日志消息喵。
     *
     * @param msg 错误内容喵
     * @param e   异常对象喵
     */
    public static void error(String msg, Throwable e) {
        PrintWriter w = out;
        if (w == null) {
            System.err.println("[GameLog-Fallback-Error] " + msg + " 喵");
            if (e != null)
                e.printStackTrace();
            return;
        }
        w.println("[" + fmtTs(System.currentTimeMillis()) + "] ERROR: " + msg + " 喵");
        if (e != null) {
            e.printStackTrace(w);
        }
    }

    public static synchronized void close() {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (Exception ignored) {
            }
            out = null;
        }
        inited = false;
        lastLogTimeByKey.clear();
    }
}
