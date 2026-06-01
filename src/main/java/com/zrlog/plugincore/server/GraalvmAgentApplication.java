package com.zrlog.plugincore.server;

import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.hibegin.common.util.Pid;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.plugin.common.PluginNativeImageUtils;
import com.zrlog.plugin.message.CapabilityInvokeRequest;
import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.NotificationRequest;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.message.SchedulerQueryRequest;
import com.zrlog.plugin.message.SchedulerQueryResult;
import com.zrlog.plugin.message.SchedulerUpdateRequest;
import com.zrlog.plugin.message.SchedulerUpdateResult;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginCoreSetting;
import com.zrlog.plugincore.server.vo.PluginVO;
import com.zrlog.plugincore.server.runtime.capability.CapabilityDocument;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventPublishResult;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventRequest;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogDocument;
import com.zrlog.plugincore.server.runtime.notification.NotificationDelivery;
import com.zrlog.plugincore.server.runtime.notification.NotificationDeliveryDocument;
import com.zrlog.plugincore.server.runtime.notification.NotificationPublishResult;
import com.zrlog.plugincore.server.runtime.notification.NotificationProviderSetting;
import com.zrlog.plugincore.server.runtime.notification.NotificationSetting;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationDocument;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationRunDocument;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomation;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomationRun;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerProviderSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerTickResult;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderSetting;
import com.zrlog.plugincore.server.runtime.service.ServiceSetting;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeInstanceState;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeInstanceView;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeState;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateDocument;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class GraalvmAgentApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        RunConstants.runType = RunType.AGENT;
        PluginNativeImageUtils.usedGsonObject();
        PluginNativeImageUtils.gsonNativeAgentByClazz(runtimeGsonClasses());
        NativeRuntimeWarmup.run();
        Application.init();
        Pid.get();
        String basePath = System.getProperty("user.dir").replace("\\target", "").replace("/target", "");
        PathUtil.setRootPath(basePath);
        File file = new File(basePath + "/src/main/resources");
        PluginNativeImageUtils.doLoopResourceLoad(file.listFiles(), file.getPath() + "/", "/");
        Application.main(args);
    }

    static List<Class<?>> runtimeGsonClasses() {
        return Arrays.asList(
                PluginVO.class,
                PluginCoreSetting.class,
                PluginCore.class,
                Plugin.class,
                PluginCapability.class,
                CapabilityDocument.class,
                CapabilityInvokeRequest.class,
                CapabilityInvokeResult.class,
                RuntimeEventRequest.class,
                RuntimeEventPublishResult.class,
                CapabilityInvocationLog.class,
                InvocationLogDocument.class,
                PluginAutomation.class,
                PluginAutomationRun.class,
                AutomationDocument.class,
                AutomationRunDocument.class,
                SchedulerSetting.class,
                SchedulerProviderSetting.class,
                SchedulerTickResult.class,
                SchedulerQueryRequest.class,
                SchedulerQueryResult.class,
                SchedulerUpdateRequest.class,
                SchedulerUpdateResult.class,
                PluginRuntimeSetting.class,
                NotificationRequest.class,
                NotificationDelivery.class,
                NotificationDeliveryDocument.class,
                NotificationPublishResult.class,
                NotificationProviderSetting.class,
                NotificationSetting.class,
                ServiceSetting.class,
                ServiceProviderSetting.class,
                PluginRuntimeInstanceState.class,
                PluginRuntimeInstanceView.class,
                PluginRuntimeState.class,
                PluginRuntimeStateDocument.class
        );
    }
}
