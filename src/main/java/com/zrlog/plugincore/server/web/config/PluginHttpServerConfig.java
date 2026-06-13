package com.zrlog.plugincore.server.web.config;

import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;
import com.zrlog.plugincore.server.runtime.PluginRuntimeServices;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerExternalEndpoint;
import com.zrlog.plugincore.server.web.controller.PluginApiController;
import com.zrlog.plugincore.server.web.controller.PluginController;
import com.zrlog.plugincore.server.web.controller.RuntimeNotificationApiController;
import com.zrlog.plugincore.server.web.controller.RuntimeSchedulerApiController;
import com.zrlog.plugincore.server.web.controller.RuntimeServiceApiController;
import com.zrlog.plugincore.server.web.controller.RuntimeStateApiController;
import com.zrlog.plugincore.server.web.controller.SettingController;
import com.zrlog.plugincore.server.web.controller.open.SchedulerController;
import com.zrlog.plugincore.server.web.handler.PluginHandle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

public class PluginHttpServerConfig extends AbstractServerConfig {

    private final Integer port;

    private final ServerConfig serverConfig;

    public PluginHttpServerConfig(Integer port) {
        this.port = port;
        this.serverConfig = initServerConfig();
    }

    public PluginHttpServerConfig(Integer port, PluginRuntimeServices runtimeServices) {
        this.port = port;
        PluginRuntimeBridge.install(Objects.requireNonNull(runtimeServices));
        this.serverConfig = initServerConfig();
    }

    @Override
    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /**
     * "" 为正式环境使用，/admin/plugins 仅为早期兼容跳转路由
     *
     * @return
     */
    private static List<String> getBasePath() {
        return List.of("", PluginHandle.OLD_PATH);
    }

    private ServerConfig initServerConfig() {
        ServerConfig serverConfig = new ServerConfig().setApplicationName("zrlog-plugin-http-server").setDisablePrintWebServerInfo(true);
        serverConfig.setNativeImageAgent(RunConstants.runType == RunType.AGENT);
        serverConfig.setHost("127.0.0.1");
        serverConfig.setPort(port);
        serverConfig.setDisableSession(true);
        serverConfig.setDisableSavePidFile(true);
        serverConfig.getInterceptors().add(PluginInterceptor.class);
        serverConfig.getInterceptors().add(MethodInterceptor.class);
        serverConfig.addErrorHandle(404, new PluginHandle());
        //open
        serverConfig.getRouter().addMapper(SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH, SchedulerController.class, "tick");
        //真实访问路由
        serverConfig.getRouter().addMapper(SchedulerExternalEndpoint.EXTERNAL_TICK_REAL_PATH, SchedulerController.class, "tick");
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
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/settings", RuntimeSchedulerApiController.class, "schedulerSettings");
        serverConfig.getRouter().addMapper(prefix + "/runtime-scheduler/tick", RuntimeSchedulerApiController.class, "schedulerTick");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations", RuntimeSchedulerApiController.class, "automations");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/update", RuntimeSchedulerApiController.class, "automationUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/run", RuntimeSchedulerApiController.class, "automationRunNow");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automations/delete", RuntimeSchedulerApiController.class, "automationDelete");
        serverConfig.getRouter().addMapper(prefix + "/runtime-automation-runs", RuntimeSchedulerApiController.class, "automationRuns");
        serverConfig.getRouter().addMapper(prefix + "/runtime-capabilities", RuntimeStateApiController.class, "capabilities");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states", RuntimeStateApiController.class, "runtimeStates");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states/start", RuntimeStateApiController.class, "runtimeStart");
        serverConfig.getRouter().addMapper(prefix + "/runtime-states/stop", RuntimeStateApiController.class, "runtimeStop");
        serverConfig.getRouter().addMapper(prefix + "/runtime-settings", RuntimeStateApiController.class, "runtimeSettings");
        serverConfig.getRouter().addMapper(prefix + "/runtime-invocation-logs", RuntimeStateApiController.class, "invocationLogs");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/channels", RuntimeNotificationApiController.class, "notificationChannels");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/provider", RuntimeNotificationApiController.class, "notificationProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/provider/auto", RuntimeNotificationApiController.class, "notificationProviderAuto");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/test", RuntimeNotificationApiController.class, "notificationTest");
        serverConfig.getRouter().addMapper(prefix + "/runtime-notification/deliveries", RuntimeNotificationApiController.class, "notificationDeliveries");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/providers", RuntimeServiceApiController.class, "serviceProviders");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/provider", RuntimeServiceApiController.class, "serviceProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/provider/auto", RuntimeServiceApiController.class, "serviceProviderAuto");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-providers", RuntimeServiceApiController.class, "commentProviders");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-provider", RuntimeServiceApiController.class, "commentProviderUpdate");
        serverConfig.getRouter().addMapper(prefix + "/runtime-services/comment-provider/default", RuntimeServiceApiController.class, "commentProviderDefault");
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
