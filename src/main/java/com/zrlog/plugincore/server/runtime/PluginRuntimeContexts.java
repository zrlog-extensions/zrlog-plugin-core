package com.zrlog.plugincore.server.runtime;

import java.util.Objects;

public final class PluginRuntimeContexts {

    private static volatile PluginRuntimeContext current = PluginRuntimeContext.unconfigured();

    private PluginRuntimeContexts() {
    }

    public static PluginRuntimeContext current() {
        return current;
    }

    public static void install(PluginRuntimeContext context) {
        current = Objects.requireNonNull(context);
    }
}
