package com.zrlog.plugincore.server.config;

import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.controller.PluginApiController;
import com.zrlog.plugincore.server.controller.PluginController;
import com.zrlog.plugincore.server.controller.RuntimeApiController;
import com.zrlog.plugincore.server.controller.SettingController;
import com.zrlog.plugincore.server.controller.open.SchedulerController;
import com.zrlog.plugincore.server.handle.PluginHandle;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerExternalEndpoint;

import java.util.Arrays;
import java.util.List;
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

    /**
     * "" 为正式环境使用，/admin/plugins 仅为兼容跳转路由
     *
     * @return
     */
    private static List<String> getBasePath() {
        if (RunConstants.runType == RunType.DEV) {
            return Arrays.asList("", "/admin/plugins");
        }
        return List.of("/admin/plugins");
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
        //open
        serverConfig.getRouter().addMapper(SchedulerExternalEndpoint.EXTERNAL_TICK_PATH, SchedulerController.class, "tick");
        for (String basePath : getBasePath()) {
            addApiMappers(serverConfig, basePath + "/api");
            addPageMappers(serverConfig, basePath);
            serverConfig.addStaticResourceMapper(basePath + "/static", "/static/static");
        }
        serverConfig.setRequestExecutor(Executors.newFixedThreadPool(50));
        serverConfig.setDecodeExecutor(Executors.newFixedThreadPool(10));
        //optimize cpu usage
        serverConfig.setSelectNowSleepTime(200);
        return serverConfig;
    }

    /**
     * Adds multiple page mappers to the server configuration, associating specified URL paths with
     * the {@code index} method of the {@link PluginController} class.
     *
     * @param serverConfig the server configuration to which the mappers are added
     * @param prefix       the common prefix for the URLs being mapped
     */
    private void addPageMappers(ServerConfig serverConfig, String prefix) {
        serverConfig.getRouter().addMapper(prefix, PluginController.class);
        serverConfig.getRouter().addMapper(prefix + "/downloadResult", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/pluginStarted", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/runs", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/settings", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification", PluginController.class, "index");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services", PluginController.class, "index");
    }

    private void addApiMappers(ServerConfig serverConfig, String prefix) {
        serverConfig.getRouter().addMapper(prefix, PluginApiController.class);
        serverConfig.getRouter().addMapper(prefix + "/setting", SettingController.class);
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/settings", RuntimeApiController.class, "schedulerSettings");
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/tick", RuntimeApiController.class, "schedulerTick");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations", RuntimeApiController.class, "automations");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/update", RuntimeApiController.class, "automationUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/run", RuntimeApiController.class, "automationRunNow");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/delete", RuntimeApiController.class, "automationDelete");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automation-runs", RuntimeApiController.class, "automationRuns");
        serverConfig.getRouter().addMapper(prefix + "/runtime-capabilities", RuntimeApiController.class, "capabilities");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states", RuntimeApiController.class, "runtimeStates");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states/start", RuntimeApiController.class, "runtimeStart");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states/stop", RuntimeApiController.class, "runtimeStop");
        serverConfig.getRouter().addMapper(prefix + "/runtime-settings", RuntimeApiController.class, "runtimeSettings");
        serverConfig.getRouter().addMapper(prefix + "/runtime-invocation-logs", RuntimeApiController.class, "invocationLogs");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/channels", RuntimeApiController.class, "notificationChannels");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/provider", RuntimeApiController.class, "notificationProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/provider/auto", RuntimeApiController.class, "notificationProviderAuto");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/deliveries", RuntimeApiController.class, "notificationDeliveries");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/providers", RuntimeApiController.class, "serviceProviders");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/provider", RuntimeApiController.class, "serviceProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/provider/auto", RuntimeApiController.class, "serviceProviderAuto");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-providers", RuntimeApiController.class, "commentProviders");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-provider", RuntimeApiController.class, "commentProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-provider/default", RuntimeApiController.class, "commentProviderDefault");
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
