package staraxis.webnet.core;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GameLog {

    private static final Path LOG_PATH = Path.of("gamedata/logs/game.log");

    private static volatile PrintWriter out;

    private static volatile boolean inited;

    private GameLog() {
    }

    public static synchronized void initTruncate() {
        if (inited) {
            return;
        }
        inited = true;

        try {
            Files.createDirectories(LOG_PATH.getParent());
        } catch (Exception ignored) {
        }

        try {
            File f = LOG_PATH.toFile();
            out = new PrintWriter(new FileWriter(f, false), true);
            out.println("[game.log] init (truncate)");
        } catch (Exception e) {
            out = null;
        }
    }

    public static void log(String msg) {
        PrintWriter w = out;
        if (w == null) {
            return;
        }
        w.println("[" + System.currentTimeMillis() + "] " + msg);
    }

    public static synchronized void close() {
        if (out != null) {
            try {
                out.flush();
            } catch (Exception ignored) {
            }
            try {
                out.close();
            } catch (Exception ignored) {
            }
            out = null;
        }
        inited = false;
    }
}
