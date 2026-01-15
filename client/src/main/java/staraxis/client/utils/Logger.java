package staraxis.client.utils;

import java.time.Instant;

public class Logger {

    public static void info(String message) {
        System.out.println(Instant.now() + " [INFO] " + message);
    }

    public static void error(String message, Throwable throwable) {
        System.err.println(Instant.now() + " [ERROR] " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }
}
