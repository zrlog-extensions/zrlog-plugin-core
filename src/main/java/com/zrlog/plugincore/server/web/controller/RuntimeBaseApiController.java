package com.zrlog.plugincore.server.web.controller;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.RuntimeCapabilityInvokerFactory;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogStore;
import com.zrlog.plugincore.server.runtime.notification.NotificationDeliveryStore;
import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationRunStore;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationStore;
import com.zrlog.plugincore.server.runtime.scheduler.RuntimeAutomationService;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerRuntime;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerTickService;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;
import com.zrlog.plugincore.server.web.util.PageDataUtils;

import java.util.List;

abstract class RuntimeBaseApiController extends Controller {

    protected <T> PageData<T> newestPage(List<T> items, int defaultPageSize) {
        return PageDataUtils.newestFirst(items, intParam("page", 1), intParam("pageSize", defaultPageSize), defaultPageSize);
    }

    protected int intParam(String name, int fallback) {
        String value = getRequest().getParaToStr(name);
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    protected String normalizeNullable(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    protected String schedulerFallbackHomeUrl(HttpRequest request) {
        return request.getScheme() + "://" + request.getHeader("Host") + request.getContextPath();
    }

    protected RuntimeAutomationService automationService() {
        KvRepository kvStore = kvStore();
        return new RuntimeAutomationService(new AutomationStore(kvStore), new CapabilityStore(kvStore), new BasicCronParser());
    }

    protected RuntimeAutomationService automationService(PluginRuntimeSetting runtimeSetting) {
        KvRepository kvStore = kvStore();
        return new RuntimeAutomationService(new AutomationStore(kvStore), new CapabilityStore(kvStore), new BasicCronParser(), runtimeSetting);
    }

    protected SchedulerTickService schedulerTickService(PluginCore pluginCore) {
        KvRepository kvStore = kvStore();
        SchedulerRuntime runtime = new SchedulerRuntime(
                new AutomationStore(kvStore),
                new AutomationRunStore(kvStore),
                new CapabilityStore(kvStore),
                RuntimeCapabilityInvokerFactory.socket(kvStore, pluginCore),
                new BasicCronParser(),
                pluginCore
        );
        return new SchedulerTickService(pluginCore.getSetting().getScheduler(), runtime);
    }

    protected AutomationRunStore automationRunStore() {
        return new AutomationRunStore(kvStore());
    }

    protected CapabilityStore capabilityStore() {
        return new CapabilityStore(kvStore());
    }

    protected NotificationDeliveryStore notificationDeliveryStore() {
        return new NotificationDeliveryStore(kvStore());
    }

    protected PluginRuntimeStateService runtimeStateService(PluginCore pluginCore) {
        KvRepository kvStore = kvStore();
        return new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), new DefaultPluginRuntimeStarter(pluginCore));
    }

    protected PluginBootstrapService pluginBootstrap() {
        return PluginRuntimeBridge.pluginBootstrap();
    }

    protected int activeInvocationCount(String pluginId) {
        return runtimeStateStore().find(pluginId)
                .map(state -> state.getActiveInvocationCount() == null ? 0 : state.getActiveInvocationCount())
                .orElse(0);
    }

    protected PluginRuntimeStateStore runtimeStateStore() {
        return new PluginRuntimeStateStore(kvStore());
    }

    protected InvocationLogStore invocationLogStore() {
        return new InvocationLogStore(kvStore());
    }

    protected KvRepository kvStore() {
        return new WebsiteRuntimeKvStore();
    }

    protected static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
