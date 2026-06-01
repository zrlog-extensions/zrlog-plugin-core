package com.zrlog.plugincore.server.runtime;

import com.zrlog.plugincore.server.plugin.PluginArtifactBootstrapper;
import com.zrlog.plugincore.server.plugin.PluginBootstrapService;
import com.zrlog.plugincore.server.plugin.PluginLifecycleService;
import com.zrlog.plugincore.server.plugin.PluginMetadataBootstrapper;
import com.zrlog.plugincore.server.plugin.PluginProcessRuntime;
import com.zrlog.plugincore.server.plugin.PluginSessionRegistry;
import com.zrlog.plugincore.server.plugin.PluginStartupCoordinator;

import java.util.HashMap;
import java.util.Map;

public final class PluginRuntimeContext {

    // Composition root only. Add wiring here; keep runtime behavior in the services.
    private static final PluginRuntimeContext INSTANCE = defaultContext();

    private final PluginBootstrapService pluginBootstrap;
    private final PluginSessionRegistry pluginSessions;

    private PluginRuntimeContext(PluginBootstrapService pluginBootstrap, PluginSessionRegistry pluginSessions) {
        this.pluginBootstrap = pluginBootstrap;
        this.pluginSessions = pluginSessions;
    }

    public static PluginRuntimeContext current() {
        return INSTANCE;
    }

    public PluginBootstrapService pluginBootstrap() {
        return pluginBootstrap;
    }

    public PluginSessionRegistry pluginSessions() {
        return pluginSessions;
    }

    private static PluginRuntimeContext defaultContext() {
        Map<String, String> requiredPlugins = requiredPlugins();
        PluginSessionRegistry sessionRegistry = new PluginSessionRegistry();
        PluginProcessRuntime processRuntime = new PluginProcessRuntime(sessionRegistry);
        PluginLifecycleService lifecycleService = new PluginLifecycleService(processRuntime, sessionRegistry);
        PluginMetadataBootstrapper metadataBootstrapper =
                new PluginMetadataBootstrapper(processRuntime, sessionRegistry, lifecycleService::stopPlugin);
        PluginArtifactBootstrapper artifactBootstrapper =
                new PluginArtifactBootstrapper(requiredPlugins, metadataBootstrapper, sessionRegistry);
        PluginStartupCoordinator startupCoordinator =
                new PluginStartupCoordinator(processRuntime, artifactBootstrapper);
        return new PluginRuntimeContext(new PluginBootstrapService(
                requiredPlugins, startupCoordinator, metadataBootstrapper, lifecycleService), sessionRegistry);
    }

    private static Map<String, String> requiredPlugins() {
        Map<String, String> requiredPlugins = new HashMap<>();
        requiredPlugins.put("comment", "comment");
        return requiredPlugins;
    }
}
