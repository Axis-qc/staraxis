package staraxis.logging;

import com.badlogic.gdx.ApplicationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GdxToSlf4jLogger implements ApplicationLogger {

    private static Logger logger(String tag) {
        return LoggerFactory.getLogger(tag != null ? tag : "Gdx");
    }

    @Override
    public void log(String tag, String message) {
        logger(tag).info(message);
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        logger(tag).info(message, exception);
    }

    @Override
    public void error(String tag, String message) {
        logger(tag).error(message);
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        logger(tag).error(message, exception);
    }

    @Override
    public void debug(String tag, String message) {
        logger(tag).debug(message);
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        logger(tag).debug(message, exception);
    }
}
