package staraxis.webnet.core;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebNetLog 喵。
 *
 * 作用喵：
 * - webnet 模块唯一权威日志出口喵！
 *
 * 约束喵：
 * - 日志写入 gamedata/logs/webnet.log 喵！
 * - 进程启动时必须调用 initTruncate() 清空旧文件喵！
 */
public final class WebNetLog {

    private static final Path LOG_PATH = Path.of("gamedata/logs/webnet.log");

    private static volatile PrintWriter out;

    private static volatile boolean inited;

    /** 频率限制用：key -> 上次打印时间戳（毫秒）喵。 */
    private static final Map<String, Long> lastLogTimeByKey = new ConcurrentHashMap<>();

    /** 同一 key 的最小间隔（毫秒），默认 60 秒喵。 */
    private static final long THROTTLE_INTERVAL_MS = 60_000L;

    private WebNetLog() {
    }

    /**
     * 初始化并截断日志文件喵。
     * 仅在进程启动时调用一次喵。
     */
    public static synchronized void initTruncate() {
        if (inited) {
            return;
        }
        inited = true;

        try {
            Files.createDirectories(LOG_PATH.getParent());
            out = new PrintWriter(new FileWriter(LOG_PATH.toFile(), false), true);
            out.println("[webnet.log] init (truncate) at " + System.currentTimeMillis() + " 喵");
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
            System.out.println("[WebNetLog-Fallback] " + msg + " 喵");
            return;
        }
        w.println("[" + System.currentTimeMillis() + "] " + msg + " 喵");
    }

    /**
     * 按 key 限流的日志打印喵：同一 key 在 60 秒内最多打印一次喵。
     *
     * @param key 业务 key（如 snapshot_stats、ai_keepalive 等）喵
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
