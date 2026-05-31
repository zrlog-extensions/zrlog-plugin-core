package com.zrlog.plugincore.server.dao;

import com.hibegin.common.util.EnvKit;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

final class DaoTrace {

    private static final String TRACE_PROPERTY = "zrlog.plugin.dao.trace";
    private static final String TRACE_ENV = "ZRLOG_PLUGIN_DAO_TRACE";

    private DaoTrace() {
    }

    static void info(Logger logger, String action, String message) {
        if (!enabled()) {
            return;
        }
        String suffix = message == null || message.trim().isEmpty() ? "" : " " + message;
        logger.info("[dao-trace] " + action + suffix + " caller=" + caller());
    }

    static String valueSummary(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            return value.getClass().getSimpleName() + "(len=" + value.toString().length() + ")";
        }
        if (value instanceof Collection) {
            return value.getClass().getSimpleName() + "(size=" + ((Collection<?>) value).size() + ")";
        }
        if (value instanceof Map) {
            return value.getClass().getSimpleName() + "(size=" + ((Map<?, ?>) value).size() + ")";
        }
        return value.getClass().getSimpleName();
    }

    static String keysSummary(Collection<String> keys) {
        if (keys == null) {
            return "keys=null";
        }
        return "keys(size=" + keys.size() + ")=" + keys;
    }

    private static boolean enabled() {
        return EnvKit.isDevMode()
                || Boolean.getBoolean(TRACE_PROPERTY)
                || "true".equalsIgnoreCase(System.getenv(TRACE_ENV));
    }

    private static String caller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(DaoTrace.class.getName())
                    || className.equals(WebSiteDAO.class.getName())
                    || className.equals(PluginCoreDAO.class.getName())
                    || className.startsWith("com.zrlog.plugincore.server.runtime.store.")
                    || className.endsWith("Store")
                    || className.startsWith("java.")) {
                continue;
            }
            return simpleName(className) + "." + element.getMethodName() + ":" + element.getLineNumber();
        }
        return "unknown";
    }

    private static String simpleName(String className) {
        int index = className.lastIndexOf('.');
        if (index < 0 || index + 1 >= className.length()) {
            return className;
        }
        return className.substring(index + 1);
    }
}
