package com.zrlog.plugincore.server;

import com.zrlog.plugincore.server.runtime.PluginCoreRunMode;
import com.zrlog.plugincore.server.runtime.PluginRuntimeContext;
import com.zrlog.plugincore.server.runtime.PluginRuntimeContexts;
import com.zrlog.plugincore.server.runtime.plugin.PluginRuntimeServer;
import com.zrlog.plugincore.server.runtime.plugin.transport.PluginNioServer;
import com.zrlog.plugincore.server.runtime.scheduler.InternalSchedulerRunner;
import com.zrlog.plugincore.server.web.PluginHttpServer;

class ApplicationServers {

    void start(Integer httpPort, PluginRuntimeContext context) {
        PluginRuntimeContexts.install(context);
        PluginRuntimeServer runtimeServer = new PluginRuntimeServer(
                new PluginNioServer(context),
                context.pluginBootstrap(),
                InternalSchedulerRunner::start
        );
        if (!runtimeServer.start(PluginCoreRunMode.shouldBootstrapRuntimeWorkers())) {
            return;
        }
        try {
            new PluginHttpServer().start(httpPort, PluginCoreRunMode.isNativeAgent(), context);
        } catch (RuntimeException e) {
            runtimeServer.stop("plugin http server failed");
            throw e;
        }
    }
}
