package com.zrlog.plugincore.server.runtime.plugin;

import com.zrlog.plugincore.server.runtime.PluginRuntimeContext;
import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.plugin.transport.PluginNioServer;
import com.zrlog.plugincore.server.runtime.scheduler.InternalSchedulerRunner;

public class PluginRuntimeServer {

    private final PluginNioServer nioServer;
    private final PluginBootstrapService pluginBootstrap;
    private final Runnable schedulerStarter;

    public PluginRuntimeServer() {
        this(new PluginNioServer(), PluginRuntimeContext.current().pluginBootstrap(), InternalSchedulerRunner::start);
    }

    PluginRuntimeServer(PluginNioServer nioServer, PluginBootstrapService pluginBootstrap, Runnable schedulerStarter) {
        this.nioServer = nioServer;
        this.pluginBootstrap = pluginBootstrap;
        this.schedulerStarter = schedulerStarter;
    }

    public boolean start(boolean bootstrapRuntimeWorkers) {
        if (bootstrapRuntimeWorkers) {
            pluginBootstrap.verifyPluginCoreReadable();
        }
        if (!nioServer.start()) {
            return false;
        }
        try {
            if (bootstrapRuntimeWorkers) {
                pluginBootstrap.loadPluginsAsync();
                schedulerStarter.run();
            }
            return true;
        } catch (RuntimeException e) {
            stop("plugin bootstrap failed");
            throw e;
        }
    }

    public void stop(String reason) {
        nioServer.stop(reason);
    }
}
