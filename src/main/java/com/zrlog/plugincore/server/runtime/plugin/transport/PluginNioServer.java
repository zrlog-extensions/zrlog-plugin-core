package com.zrlog.plugincore.server.runtime.plugin.transport;

import com.hibegin.http.server.api.ISocketServer;
import com.zrlog.plugincore.server.runtime.PluginRuntimeContext;

public class PluginNioServer {

    private final ISocketServer socketServer;

    public PluginNioServer() {
        this(new PluginCoreSocketServer());
    }

    public PluginNioServer(PluginRuntimeContext context) {
        this(new PluginCoreSocketServer(context.pluginConfig(), context.pluginBootstrap()));
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
