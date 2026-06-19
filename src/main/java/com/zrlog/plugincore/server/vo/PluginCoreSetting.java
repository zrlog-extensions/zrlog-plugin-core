package com.zrlog.plugincore.server.vo;

import com.zrlog.plugincore.server.runtime.notification.NotificationSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerSetting;
import com.zrlog.plugincore.server.runtime.service.ServiceSetting;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;

public class PluginCoreSetting {

    private boolean disableAutoDownloadLostFile;
    private SchedulerSetting scheduler;
    private NotificationSetting notification;
    private ServiceSetting service;
    private PluginRuntimeSetting runtime;

    public boolean isDisableAutoDownloadLostFile() {
        return !isAutoDownloadMissingPluginFileEnabled();
    }

    public void setDisableAutoDownloadLostFile(boolean disableAutoDownloadLostFile) {
        this.disableAutoDownloadLostFile = disableAutoDownloadLostFile;
        getRuntime().setAutoDownloadMissingPluginFileEnabled(!disableAutoDownloadLostFile);
    }

    public boolean isAutoDownloadMissingPluginFileEnabled() {
        Boolean runtimeValue = runtime == null ? null : runtime.getConfiguredAutoDownloadMissingPluginFileEnabled();
        if (runtimeValue != null) {
            return runtimeValue;
        }
        return !disableAutoDownloadLostFile;
    }

    public void setAutoDownloadMissingPluginFileEnabled(boolean autoDownloadMissingPluginFileEnabled) {
        this.disableAutoDownloadLostFile = !autoDownloadMissingPluginFileEnabled;
        getRuntime().setAutoDownloadMissingPluginFileEnabled(autoDownloadMissingPluginFileEnabled);
    }

    public SchedulerSetting getScheduler() {
        if (scheduler == null) {
            scheduler = new SchedulerSetting();
        }
        scheduler.ensureDefaultProvider();
        return scheduler;
    }

    public void setScheduler(SchedulerSetting scheduler) {
        this.scheduler = scheduler;
    }

    public NotificationSetting getNotification() {
        if (notification == null) {
            notification = new NotificationSetting();
        }
        return notification;
    }

    public void setNotification(NotificationSetting notification) {
        this.notification = notification;
    }

    public ServiceSetting getService() {
        if (service == null) {
            service = new ServiceSetting();
        }
        return service;
    }

    public void setService(ServiceSetting service) {
        this.service = service;
    }

    public PluginRuntimeSetting getRuntime() {
        if (runtime == null) {
            runtime = new PluginRuntimeSetting();
        }
        if (runtime.getConfiguredAutoDownloadMissingPluginFileEnabled() == null) {
            runtime.setAutoDownloadMissingPluginFileEnabled(!disableAutoDownloadLostFile);
        }
        return runtime;
    }

    public void setRuntime(PluginRuntimeSetting runtime) {
        this.runtime = runtime;
    }
}
