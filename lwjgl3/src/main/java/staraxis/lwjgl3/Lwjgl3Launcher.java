package staraxis.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import staraxis.ClientGame;

import java.io.File;

/**
 * LWJGL3 启动器。
 *
 * 设计要点：
 * - 只负责平台侧窗口创建与启动，不承载任何游戏规则逻辑（遵循分层边界）。
 * - 启动时提前确保日志目录存在，避免 logback 由于目录缺失导致无法创建 game.log/error.log。
 */
public class Lwjgl3Launcher {

    private static final Logger log = LoggerFactory.getLogger(Lwjgl3Launcher.class);

    public static void main(String[] args) {
        ensureLogDir();
        if (StartupHelper.startNewJvmIfRequired())
            return;

        log.info("Launcher start, log dir ready.");
        createApplication();
    }

    private static void ensureLogDir() {
        // NOTE: `:lwjgl3:run` 在 build.gradle 中将 workingDir 设为 assets/，因此 user.dir 指向
        // assets。
        // 为了把日志稳定落到“项目根目录/gamedata/logs”，这里使用 ../gamedata/logs。
        File dir = new File("../gamedata/logs");
        if (!dir.exists())
            dir.mkdirs();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new ClientGame(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("staraxis");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(640, 480);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);
        return configuration;
    }
}
