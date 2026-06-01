package com.zrlog.plugincore.server.web;

import com.hibegin.http.server.WebServerBuilder;
import com.zrlog.plugincore.server.runtime.PluginRuntimeServices;
import com.zrlog.plugincore.server.web.config.PluginHttpServerConfig;

public class PluginHttpServer {

    public void start(Integer serverPort, boolean nativeAgent, PluginRuntimeServices services) {
        PluginHttpServerConfig config = new PluginHttpServerConfig(serverPort, services);
        WebServerBuilder build = new WebServerBuilder.Builder().config(config).build();
        if (nativeAgent) {
            config.getServerConfig().addCreateSuccessHandle(() -> {
                Thread.sleep(5000);
                System.exit(0);
                return null;
            });
        }
        build.start();
    }
}
