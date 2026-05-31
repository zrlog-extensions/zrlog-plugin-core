package com.zrlog.plugincore.server.runtime.state;

import java.util.Optional;

public interface PluginRuntimeStarter {

    boolean isStarted(String pluginId);

    Optional<PluginIdentity> findPlugin(String pluginId);

    default String runtimeMode(PluginIdentity identity) {
        return "process";
    }

    default boolean managesRuntimeState() {
        return false;
    }

    default void cleanupStartFailure(PluginIdentity identity) {
    }

    void start(PluginIdentity identity);
}
