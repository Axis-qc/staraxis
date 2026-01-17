package staraxis.logging;

import com.badlogic.gdx.ApplicationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将 libGDX 的 {@link com.badlogic.gdx.Gdx#app} 日志接口桥接到 slf4j/logback。
 *
 * 设计要点：
 * - 工程历史代码大量使用 `Gdx.app.log/error/debug`；直接全量替换会造成大范围变更。
 * - 通过桥接方式把日志统一汇总到 logback（最终落盘到 gamedata/logs），避免多套日志系统并存。
 * - tag 作为 logger 名称使用，便于在日志中按模块/组件过滤。
 */
public class GdxToSlf4jLogger implements ApplicationLogger {

    private static Logger logger(String tag) {
        // NOTE: tag 可能为 null，统一回落到 "Gdx"，避免产生空 logger 名。
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
        // NOTE: error 级别会进入 error.log（由 logback.xml 的 ThresholdFilter 控制）。
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
