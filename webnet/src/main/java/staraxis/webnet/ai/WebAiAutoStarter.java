package staraxis.webnet.ai;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebAiAutoStarter
 *
 * 作用（description）：
 * - 负责监控并在必要时自动拉起 AI 助手进程（ai_system/run_agent.bat）喵。
 * - 仅在首个玩家连接（WS/HTTP）时触发，避免后台空转喵。
 * - 支持基于空闲时间的自动退出（auto_exit & idle_exit_seconds）喵。
 *
 * 提供的接口/API：
 * - ensureAiStartedIfNeeded()：幂等的启动触发入口喵。
 * - reportActivity()：上报玩家活动，刷新空闲计时喵。
 *
 * 注意事项（important_notes）：
 * - 严格解析 config.yaml 的 server 段，通过缩进与去注释确保解析准确喵。
 * - show_console: true 模式下使用 cmd start 启动，不保证空闲时能自动关闭子进程喵。
 * - 静态注册单一 Shutdown Hook，确保 JVM 退出时清理资源喵。
 */
public class WebAiAutoStarter {

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static Process aiProcess = null;
    private static volatile long lastActivityAtMs = 0;

    // 运行中配置快照（启动时锁定）喵
    private static boolean runningShowConsole = false;
    private static boolean runningAutoExit = true;
    private static long runningIdleTimeoutMs = 120 * 1000L;

    static {
        // 静态注册单一关闭钩子喵
        Runtime.getRuntime().addShutdownHook(new Thread(WebAiAutoStarter::stopProcess, "ai-system-shutdown"));
    }

    /**
     * 上报玩家活动，刷新空闲计时喵。
     */
    public static void reportActivity() {
        lastActivityAtMs = System.currentTimeMillis();
    }

    /**
     * 检查并尝试启动 AI 助手喵。
     */
    public static void ensureAiStartedIfNeeded() {
        // 活性检查：如果记录为已启动但进程实际已消失，允许重置喵
        if (started.get()) {
            if (aiProcess != null && !aiProcess.isAlive()) {
                synchronized (started) {
                    if (aiProcess != null && !aiProcess.isAlive()) {
                        started.set(false);
                        aiProcess = null;
                    }
                }
            } else {
                return;
            }
        }

        synchronized (started) {
            if (started.get())
                return;

            try {
                File configFile = new File("ai_system/config/config.yaml");
                if (!configFile.exists())
                    return;

                String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                ServerConfig cfg = parseServerConfig(content);

                if (!cfg.autoStart)
                    return;

                // 锁定运行期配置喵
                runningShowConsole = cfg.showConsole;
                runningAutoExit = cfg.autoExit;
                runningIdleTimeoutMs = cfg.idleExitSeconds * 1000L;

                // 启动即刷新活动时间喵
                reportActivity();

                launchProcess(runningShowConsole);
                started.set(true);

                if (runningAutoExit) {
                    startIdleChecker();
                }

                staraxis.webnet.core.WebNetLog
                        .log("AI Assistant triggered (showConsole=" + runningShowConsole + ", autoExit="
                                + runningAutoExit + ", idle=" + cfg.idleExitSeconds + "s) successfully喵.");
            } catch (Exception e) {
                staraxis.webnet.core.WebNetLog.log("AI AutoStart failed: " + e.getMessage() + "喵.");
            }
        }
    }

    private static void startIdleChecker() {
        Thread t = new Thread(() -> {
            while (started.get()) {
                try {
                    Thread.sleep(10000);
                    if (aiProcess == null || !aiProcess.isAlive()) {
                        started.set(false);
                        break;
                    }
                    long idleMs = System.currentTimeMillis() - lastActivityAtMs;
                    if (idleMs > runningIdleTimeoutMs) {
                        staraxis.webnet.core.WebNetLog
                                .log("AI Assistant idle for " + (idleMs / 1000) + "s, stopping...喵");
                        stopProcess();
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ai-idle-checker");
        t.setDaemon(true);
        t.start();
    }

    private static void stopProcess() {
        synchronized (started) {
            if (aiProcess != null) {
                if (aiProcess.isAlive()) {
                    if (runningShowConsole) {
                        staraxis.webnet.core.WebNetLog.log(
                                "Notice: AI was in show_console mode, automatic exit might not kill the actual window喵.");
                    }
                    aiProcess.destroy();
                    try {
                        if (!aiProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            aiProcess.destroyForcibly();
                        }
                    } catch (Exception ignored) {
                    }
                }
                aiProcess = null;
            }
            started.set(false);
        }
    }

    private static void launchProcess(boolean showConsole) throws Exception {
        File batFile = new File("ai_system/run_agent.bat");
        if (!batFile.exists())
            throw new RuntimeException("run_agent.bat missing喵");

        ProcessBuilder pb;
        if (showConsole) {
            pb = new ProcessBuilder("cmd.exe", "/c", "start", "StarAxis-AI-Assistant", "cmd.exe", "/c",
                    batFile.getAbsolutePath());
        } else {
            pb = new ProcessBuilder("cmd.exe", "/c", batFile.getAbsolutePath());
            File logFile = new File("gamedata/logs/ai_system.log");
            if (!logFile.getParentFile().exists())
                logFile.getParentFile().mkdirs();
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        }
        pb.directory(new File("ai_system"));
        aiProcess = pb.start();
    }

    // 严格的 server 段轻量解析喵
    private static ServerConfig parseServerConfig(String yaml) {
        ServerConfig cfg = new ServerConfig();
        String[] lines = yaml.split("\n");
        boolean inServerBlock = false;

        for (String line : lines) {
            String trimmed = line.split("#")[0]; // 去注释喵
            if (trimmed.isBlank())
                continue;

            if (!inServerBlock) {
                if (trimmed.trim().equals("server:")) {
                    inServerBlock = true;
                }
            } else {
                // 如果遇到新的顶层 block 则退出喵
                if (!line.startsWith(" ") && !line.startsWith("\t") && line.contains(":")) {
                    break;
                }

                String content = trimmed.trim();
                if (content.startsWith("auto_start:"))
                    cfg.autoStart = content.contains("true");
                else if (content.startsWith("show_console:"))
                    cfg.showConsole = content.contains("true");
                else if (content.startsWith("auto_exit:"))
                    cfg.autoExit = content.contains("true");
                else if (content.startsWith("idle_exit_seconds:")) {
                    try {
                        cfg.idleExitSeconds = Long.parseLong(content.split(":")[1].trim());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return cfg;
    }

    private static class ServerConfig {
        boolean autoStart = false;
        boolean showConsole = false;
        boolean autoExit = true;
        long idleExitSeconds = 120;
    }
}
