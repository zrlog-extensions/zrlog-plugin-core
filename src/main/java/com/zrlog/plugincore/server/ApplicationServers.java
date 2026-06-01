package com.zrlog.plugincore.server;

import com.zrlog.plugincore.server.runtime.PluginCoreRunMode;
import com.zrlog.plugincore.server.runtime.PluginRuntimeServices;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;
import com.zrlog.plugincore.server.runtime.plugin.PluginRuntimeServer;
import com.zrlog.plugincore.server.runtime.plugin.transport.PluginNioServer;
import com.zrlog.plugincore.server.runtime.scheduler.InternalSchedulerRunner;
import com.zrlog.plugincore.server.web.PluginHttpServer;

class ApplicationServers {

    void start(Integer httpPort, PluginRuntimeServices services) {
        PluginRuntimeBridge.install(services);
        PluginRuntimeServer runtimeServer = new PluginRuntimeServer(
                new PluginNioServer(services.pluginConfig(), services.pluginBootstrap()),
                services.pluginBootstrap(),
                InternalSchedulerRunner::start
        );
        if (!runtimeServer.start(PluginCoreRunMode.shouldBootstrapRuntimeWorkers())) {
            return;
        }
        try {
            new PluginHttpServer().start(httpPort, PluginCoreRunMode.isNativeAgent(), services);
        } catch (RuntimeException e) {
            runtimeServer.stop("plugin http server failed");
            throw e;
        }
    }
}
