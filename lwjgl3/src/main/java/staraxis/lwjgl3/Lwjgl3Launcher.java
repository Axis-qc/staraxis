package staraxis.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import staraxis.ClientGame;
import staraxis.ui.settings.GameSettings;
import staraxis.ui.settings.GpuService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * LWJGL3 启动器。
 *
 * 设计要点：
 * - 只负责平台侧窗口创建与启动，不承载任何游戏规则逻辑（遵循分层边界）。
 * - 启动时提前确保日志目录存在，避免 logback 由于目录缺失导致无法创建 game.log/error.log。
 * - 在创建窗口前，轻量级地读取 settings.json 以应用分辨率、全屏等必须在启动时配置的选项。
 */
public class Lwjgl3Launcher {

    private static final Logger log = LoggerFactory.getLogger(Lwjgl3Launcher.class);

    public static void main(String[] args) {
        ensureLogDir();
        if (StartupHelper.startNewJvmIfRequired())
            return;

        log.info("Launcher start, log dir ready.");

        // NOTE: 在 Gdx.app 创建前，只能用原生 Java IO 读取设置文件。
        // 这里只做轻量级读取，完整的 SettingsRepository 在 ClientGame.create() 中使用。
        GameSettings settings = loadSettingsPreGdx();

        // 预枚举系统 GPU（GL 未就绪，用系统命令）
        populateGpuListPreGl();

        // GL 就绪后用 GL_RENDERER 补充当前 GPU
        ClientGame.onReady = Lwjgl3Launcher::populateGpuListPostGl;

        createApplication(settings);
    }

    private static GameSettings loadSettingsPreGdx() {
        File settingsFile = new File("gamedata/settings.json");
        if (!settingsFile.exists()) {
            log.info("settings.json not found, using default settings for window creation.");
            return GameSettings.createDefault();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(settingsFile, GameSettings.class);
        } catch (Exception e) {
            log.error("Failed to parse settings.json before Gdx init, falling back to default.", e);
            return GameSettings.createDefault();
        }
    }

    private static void ensureLogDir() {
        File dir = new File("gamedata/logs");
        if (!dir.exists())
            dir.mkdirs();
    }

    private static Lwjgl3Application createApplication(GameSettings settings) {
        return new Lwjgl3Application(new ClientGame(), getDefaultConfiguration(settings));
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(GameSettings settings) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("staraxis");

        // 应用设置
        configuration.useVsync(settings.vsync);
        if (settings.fpsLimit > 0) {
            configuration.setForegroundFPS(settings.fpsLimit);
        } else {
            // 未设置 FPS 上限时限制为 240 帧，防止 CPU 空转
            configuration.setForegroundFPS(240);
        }
        // 无 vsync 时将空闲帧率设为 30，减少无操作时的 CPU 消耗
        if (!settings.vsync) {
            configuration.setIdleFPS(30);
        }

        // 显式请求 OpenGL 3.2 Core Profile，启用 GL30 模拟以暴露 gl30/gl31/gl32 API（实例化渲染需要）
        configuration.setOpenGLEmulation(
                Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 3);

        try {
            String[] parts = settings.resolution.split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            if (settings.fullscreen) {
                configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
            } else {
                configuration.setWindowedMode(width, height);
            }
        } catch (Exception e) {
            log.error("Invalid resolution format in settings: '{}', falling back to 1280x720.", settings.resolution, e);
            configuration.setWindowedMode(1280, 720);
        }

        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }

    /**
     * GL 上下文未就绪时的 GPU 枚举（系统命令）。
     * Windows 用 WMIC 查显卡名，Linux 用 lspci。
     */
    private static void populateGpuListPreGl() {
        Set<String> gpus = new LinkedHashSet<>();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // WMIC 查询显卡名称
                Process p = Runtime.getRuntime().exec(
                        new String[] { "wmic", "path", "win32_videocontroller", "get", "name" });
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.equalsIgnoreCase("Name"))
                            gpus.add(line);
                    }
                }
                p.waitFor();
            } else if (os.contains("linux")) {
                Process p = Runtime.getRuntime().exec(
                        new String[] { "lspci", "-mm" });
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("VGA") || line.contains("3D") || line.contains("Display"))
                            gpus.add(line.replaceAll(".*\"([^\"]+)\"[^\"]*$", "$1").trim());
                    }
                }
                p.waitFor();
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate GPUs via system command", e);
        }

        GpuService.available.clear();
        if (gpus.isEmpty()) {
            GpuService.available.add("Default (will detect on GL init)");
        } else {
            GpuService.available.addAll(gpus);
        }
        log.info("Pre-GL GPU enumeration: {}", GpuService.available);
    }

    /**
     * GL 上下文就绪后的 GPU 补充（GL_RENDERER）。
     * 由 ClientGame.create() 调用。把当前激活 GPU 作为首选项插入 / 替换。
     */
    private static void populateGpuListPostGl() {
        try {
            String renderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
            if (renderer != null && !renderer.isEmpty()) {
                // 在列表头部插入当前激活 GPU
                GpuService.available.remove(renderer);
                GpuService.available.add(0, renderer);
                log.info("Post-GL GPU (GL_RENDERER): {}", renderer);
            }
        } catch (Exception e) {
            log.warn("Failed to read GL_RENDERER", e);
        }

        if (GpuService.available.isEmpty()) {
            GpuService.available.add("Default");
        }

        log.info("Final GPU list: {}", GpuService.available);
    }
}
