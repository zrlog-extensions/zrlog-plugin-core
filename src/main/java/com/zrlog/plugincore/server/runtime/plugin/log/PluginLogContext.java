package com.zrlog.plugincore.server.runtime.plugin.log;

import com.hibegin.common.dao.DaoLogContext;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.message.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginLogContext {

    private static final String PLUGIN_LOG_LABEL_ATTR = "_zrlog_plugin_log_label";
    private static final Map<String, String> SHORT_NAMES_BY_PLUGIN_ID = new ConcurrentHashMap<>();
    private static final ThreadLocal<PluginLabel> CURRENT = new ThreadLocal<>();

    private PluginLogContext() {
    }

    public static Scope open(IOSession session) {
        if (session == null) {
            return open((Plugin) null);
        }
        Object label = session.getSystemAttr().get(PLUGIN_LOG_LABEL_ATTR);
        String normalizedLabel = normalize(label == null ? null : label.toString());
        Plugin plugin = session.getPlugin();
        if (normalizedLabel != null) {
            if (plugin != null) {
                register(plugin.getId(), normalizedLabel);
            }
            return open(plugin == null ? null : plugin.getId(), normalizedLabel, null);
        }
        return open(plugin);
    }

    public static void bind(IOSession session, Plugin plugin) {
        if (session == null || plugin == null) {
            return;
        }
        bind(session, plugin.getId(), plugin.getShortName());
    }

    public static void bind(IOSession session, String pluginId, String shortName) {
        String normalizedShortName = normalize(shortName);
        if (session != null && normalizedShortName != null) {
            session.getSystemAttr().put(PLUGIN_LOG_LABEL_ATTR, normalizedShortName);
        }
        register(pluginId, normalizedShortName);
    }

    public static Scope open(Plugin plugin) {
        if (plugin == null) {
            return open(null, null, null);
        }
        register(plugin.getId(), plugin.getShortName());
        return open(plugin.getId(), plugin.getShortName(), plugin.getName());
    }

    public static Scope open(String pluginId, String shortName, String name) {
        PluginLabel previous = CURRENT.get();
        String normalizedPluginId = normalize(pluginId);
        String normalizedShortName = normalize(shortName);
        if (normalizedShortName == null && normalizedPluginId != null) {
            normalizedShortName = SHORT_NAMES_BY_PLUGIN_ID.get(normalizedPluginId);
        }
        register(normalizedPluginId, normalizedShortName);
        PluginLabel next = PluginLabel.of(normalizedShortName);
        boolean hasAnyInput = normalize(pluginId) != null || normalize(shortName) != null || normalize(name) != null;
        if (next == null) {
            if (!hasAnyInput) {
                CURRENT.remove();
                return new Scope(previous, DaoLogContext.open(null), true);
            }
            return new Scope(previous, null, false);
        }
        if (previous != null && previous.hasShortName() && !next.hasShortName()) {
            return new Scope(previous, null, false);
        }
        CURRENT.set(next);
        return new Scope(previous, DaoLogContext.open(next.label()), true);
    }

    public static void register(String pluginId, String shortName) {
        String normalizedPluginId = normalize(pluginId);
        String normalizedShortName = normalize(shortName);
        if (normalizedPluginId == null || normalizedShortName == null) {
            return;
        }
        SHORT_NAMES_BY_PLUGIN_ID.put(normalizedPluginId, normalizedShortName);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return normalize(value) == null;
    }

    private static void restore(PluginLabel previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static String currentLabel() {
        PluginLabel label = CURRENT.get();
        return label == null ? null : label.label();
    }

    public static String prefix(String message) {
        String label = currentLabel();
        if (label == null) {
            return message;
        }
        return "[" + label + "] " + message;
    }

    public static final class Scope implements AutoCloseable {
        private final PluginLabel previous;
        private final DaoLogContext.Scope daoScope;
        private final boolean active;
        private boolean closed;

        private Scope(PluginLabel previous, DaoLogContext.Scope daoScope, boolean active) {
            this.previous = previous;
            this.daoScope = daoScope;
            this.active = active;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (!active) {
                return;
            }
            daoScope.close();
            restore(previous);
        }
    }

    private static final class PluginLabel {
        private final String shortName;

        private PluginLabel(String shortName) {
            this.shortName = shortName;
        }

        private static PluginLabel of(String shortName) {
            String normalizedShortName = normalize(shortName);
            if (normalizedShortName == null) {
                return null;
            }
            return new PluginLabel(normalizedShortName);
        }

        private String label() {
            return shortName;
        }

        private boolean hasShortName() {
            return !isBlank(shortName);
        }
    }
}
