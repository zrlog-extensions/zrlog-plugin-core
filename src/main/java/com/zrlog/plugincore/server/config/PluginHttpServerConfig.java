package com.zrlog.plugincore.server.config;

import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.controller.PluginApiController;
import com.zrlog.plugincore.server.controller.PluginController;
import com.zrlog.plugincore.server.controller.SettingController;
import com.zrlog.plugincore.server.handle.PluginHandle;

import java.util.concurrent.Executors;

public class PluginHttpServerConfig extends AbstractServerConfig {

    private final Integer port;

    private final ServerConfig serverConfig;

    public PluginHttpServerConfig(Integer port) {
        this.port = port;
        this.serverConfig = initServerConfig();
    }

    @Override
    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    private ServerConfig initServerConfig() {
        ServerConfig serverConfig = new ServerConfig().setApplicationName("zrlog-plugin-http-server").setDisablePrintWebServerInfo(true);
        serverConfig.setNativeImageAgent(Application.nativeAgent);
        serverConfig.setHost("127.0.0.1");
        serverConfig.setPort(port);
        serverConfig.setDisableSession(true);
        serverConfig.setDisableSavePidFile(true);
        serverConfig.getInterceptors().add(PluginInterceptor.class);
        serverConfig.getInterceptors().add(MethodInterceptor.class);
        serverConfig.addErrorHandle(404, new PluginHandle());
        //real env
        serverConfig.getRouter().addMapper("", PluginController.class);
        serverConfig.addStaticResourceMapper("/static", "/static/static");
        serverConfig.getRouter().addMapper("/api", PluginApiController.class);
        serverConfig.getRouter().addMapper("/api/setting", SettingController.class);
        //dev env
        serverConfig.getRouter().addMapper("/admin/plugins", PluginController.class);
        serverConfig.getRouter().addMapper("/admin/plugins/api", PluginApiController.class);
        serverConfig.getRouter().addMapper("/admin/plugins/setting", SettingController.class);
        serverConfig.addStaticResourceMapper("/admin/plugins/static", "/static/static");
        serverConfig.setRequestExecutor(Executors.newFixedThreadPool(50));
        serverConfig.setDecodeExecutor(Executors.newFixedThreadPool(10));
        //optimize cpu usage
        serverConfig.setSelectNowSleepTime(200);
        return serverConfig;
    }

    @Override
    public RequestConfig getRequestConfig() {
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setDisableSession(true);
        return requestConfig;
    }

    @Override
    public ResponseConfig getResponseConfig() {
        ResponseConfig responseConfig = new ResponseConfig();
        responseConfig.setEnableGzip(false);
        return responseConfig;
    }
}
