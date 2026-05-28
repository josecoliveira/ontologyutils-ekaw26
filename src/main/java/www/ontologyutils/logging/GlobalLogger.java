package www.ontologyutils.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Very small global logger wrapper using java.util.logging. Replaceable later
 * with SLF4J adapter.
 */
public final class GlobalLogger {
    private static volatile boolean enabled = false;
    private static final Logger logger = Logger.getLogger("ontologyutils");

    private GlobalLogger() {
    }

    public static void enable(boolean on) {
        enabled = on;
    }

    public static void info(String tag, String msg) {
        if (!enabled) return;
        logger.log(Level.INFO, "[{0}] {1}", new Object[]{tag, msg});
    }

    public static void debug(String tag, String msg) {
        if (!enabled) return;
        logger.log(Level.FINE, "[{0}] {1}", new Object[]{tag, msg});
    }

    public static void error(String tag, String msg, Throwable t) {
        if (!enabled) return;
        logger.log(Level.SEVERE, String.format("[%s] %s", tag, msg), t);
    }
}

