package staraxis.game.server;

/**
 * ServerConfig（服务端配置）。
 *
 * 头戴式服务端的启动参数，支持命令行覆盖。
 * 不依赖外部配置文件也能直接启动。
 *
 * 命令行示例：
 *   java -jar game.jar --seed=42 --stars=1000 --tps=20
 */
public class ServerConfig {

    /** 世界种子，null 表示使用 hashCode 种子（确定但不可预测）。 */
    public String worldSeed = null;

    /** 恒星系数量。 */
    public int starCount = 500;

    /** 每秒 Tick 数。 */
    public int ticksPerSecond = 20;

    /** 是否在启动时打印详细生成报告。 */
    public boolean verbose = false;

    /** 是否在启动后直接进入 tick 循环（否则停在控制台等待命令）。 */
    public boolean autoStart = true;

    /**
     * 从命令行参数解析配置。
     * 支持格式：--key=value 或 --key（bool）
     */
    public static ServerConfig parse(String[] args) {
        ServerConfig cfg = new ServerConfig();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String kv = arg.substring(2);
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    String key = kv.substring(0, eq);
                    String val = kv.substring(eq + 1);
                    apply(cfg, key, val);
                } else {
                    apply(cfg, kv, "true");
                }
            }
        }
        return cfg;
    }

    private static void apply(ServerConfig cfg, String key, String val) {
        switch (key) {
            case "seed" -> cfg.worldSeed = val;
            case "stars" -> cfg.starCount = parseInt(key, val, 500);
            case "tps" -> cfg.ticksPerSecond = parseInt(key, val, 20);
            case "verbose" -> cfg.verbose = val.equals("true") || val.equals("1");
            case "auto-start" -> cfg.autoStart = val.equals("true") || val.equals("1");
        }
    }

    private static int parseInt(String key, String val, int def) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            System.err.println("[WARN] \u65e0\u6548\u53c2\u6570 --" + key + "=" + val + "\uff0c\u4f7f\u7528\u9ed8\u8ba4\u503c " + def);
            return def;
        }
    }

    /**
     * 打印使用说明。
     */
    public static void printHelp() {
        System.out.println("StarAxis \u5934\u888b\u5f0f\u670d\u52a1\u7aef");
        System.out.println();
        System.out.println("\u7528\u6cd5: java -jar game.jar [\u53c2\u6570...]");
        System.out.println();
        System.out.println("\u53c2\u6570:");
        System.out.println("  --seed=<s>      \u4e16\u754c\u79cd\u5b50\uff08\u9ed8\u8ba4: \u65e0\uff0c\u4f7f\u7528 hashCode\uff09");
        System.out.println("  --stars=<n>     \u6052\u661f\u7cfb\u6570\u91cf\uff08\u9ed8\u8ba4: 500\uff09");
        System.out.println("  --tps=<n>       \u6bcf\u79d2 Tick \u6570\uff08\u9ed8\u8ba4: 20\uff09");
        System.out.println("  --verbose       \u6253\u5f00\u8be6\u7ec6\u65e5\u5fd7");
        System.out.println("  --no-auto-start \u542f\u52a8\u540e\u4e0d\u81ea\u52a8\u8fdb\u5165 tick \u5faa\u73af\uff0c\u7b49\u5f85\u547d\u4ee4");
        System.out.println("  --help          \u663e\u793a\u6b64\u5e2e\u52a9");
    }
}
