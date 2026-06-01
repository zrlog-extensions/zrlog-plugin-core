package com.zrlog.plugincore.server;

import com.zrlog.plugincore.server.runtime.PluginCoreRunMode;
import com.zrlog.plugincore.server.runtime.plugin.PluginRuntimeServer;
import com.zrlog.plugincore.server.web.PluginHttpServer;

class ApplicationServers {

    void start(Integer httpPort) {
        PluginRuntimeServer runtimeServer = new PluginRuntimeServer();
        if (!runtimeServer.start(PluginCoreRunMode.shouldBootstrapRuntimeWorkers())) {
            return;
        }
        try {
            new PluginHttpServer().start(httpPort, PluginCoreRunMode.isNativeAgent());
        } catch (RuntimeException e) {
            runtimeServer.stop("plugin http server failed");
            throw e;
        }
    }
}
