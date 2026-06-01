package com.zrlog.plugincore.server.runtime.plugin.transport;

import com.hibegin.http.server.api.ISocketServer;
import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginConfig;

public class PluginNioServer {

    private final ISocketServer socketServer;

    public PluginNioServer() {
        this(new PluginCoreSocketServer());
    }

    public PluginNioServer(PluginConfig pluginConfig, PluginBootstrapService pluginBootstrap) {
        this(new PluginCoreSocketServer(pluginConfig, pluginBootstrap));
    }

    public PluginNioServer(ISocketServer socketServer) {
        this.socketServer = socketServer;
    }

    public boolean start() {
        if (!socketServer.create()) {
            return false;
        }
        new Thread(socketServer::listen, "zrlog-plugin-socket").start();
        return true;
    }

    public void stop(String reason) {
        socketServer.destroy(reason);
    }
}
