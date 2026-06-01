package com.zrlog.plugincore.server.runtime;

import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginConfig;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginHostConnection;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessionRegistry;

import java.util.Objects;

public final class PluginRuntimeBridge {

    private static volatile PluginRuntimeServices current = PluginRuntimeServices.unconfigured();

    private PluginRuntimeBridge() {
    }

    public static void install(PluginRuntimeServices services) {
        current = Objects.requireNonNull(services);
    }

    public static PluginBootstrapService pluginBootstrap() {
        return current.pluginBootstrap();
    }

    public static PluginSessionRegistry pluginSessions() {
        return current.pluginSessions();
    }

    public static PluginConfig pluginConfig() {
        return current.pluginConfig();
    }

    public static PluginHostConnection hostConnection() {
        return current.hostConnection();
    }
}
