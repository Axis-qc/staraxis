package staraxis.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import staraxis.ClientGame;

import java.io.File;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {

    private static final Logger log = LoggerFactory.getLogger(Lwjgl3Launcher.class);

    public static void main(String[] args) {
        ensureLogDir();
        if (StartupHelper.startNewJvmIfRequired()) return;
        log.info("Launcher start, log dir ready.");
        createApplication();
    }

    private static void ensureLogDir() {
        // 注意：gradle run 的 workingDir 被设置为 assets/，因此 user.dir 指向 assets。
        // 这里用 ../gamedata/logs 固定指向项目根目录下的 gamedata/logs。
        File dir = new File("../gamedata/logs");
        if (!dir.exists()) dir.mkdirs();
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