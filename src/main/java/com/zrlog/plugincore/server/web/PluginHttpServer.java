package com.zrlog.plugincore.server.web;

import com.hibegin.http.server.WebServerBuilder;
import com.zrlog.plugincore.server.runtime.PluginRuntimeContext;
import com.zrlog.plugincore.server.web.config.PluginHttpServerConfig;

public class PluginHttpServer {

    public void start(Integer serverPort, boolean nativeAgent, PluginRuntimeContext context) {
        PluginHttpServerConfig config = new PluginHttpServerConfig(serverPort, context);
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
