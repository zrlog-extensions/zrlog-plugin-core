package com.zrlog.plugincore.server.runtime.state;

public class PluginRuntimeSetting {

    private Boolean onDemandEnabled = true;
    private Boolean autoDownloadMissingPluginFileEnabled;
    private Boolean idleStopEnabled = true;
    private Long idleTimeoutSeconds = 300L;
    private Long idleScanIntervalSeconds = 30L;

    public Boolean getOnDemandEnabled() {
        return onDemandEnabled == null ? Boolean.TRUE : onDemandEnabled;
    }

    public void setOnDemandEnabled(Boolean onDemandEnabled) {
        this.onDemandEnabled = onDemandEnabled;
    }

    public Boolean getAutoDownloadMissingPluginFileEnabled() {
        return autoDownloadMissingPluginFileEnabled == null ? Boolean.TRUE : autoDownloadMissingPluginFileEnabled;
    }

    public Boolean getConfiguredAutoDownloadMissingPluginFileEnabled() {
        return autoDownloadMissingPluginFileEnabled;
    }

    public void setAutoDownloadMissingPluginFileEnabled(Boolean autoDownloadMissingPluginFileEnabled) {
        this.autoDownloadMissingPluginFileEnabled = autoDownloadMissingPluginFileEnabled;
    }

    public Boolean getIdleStopEnabled() {
        return idleStopEnabled == null ? Boolean.TRUE : idleStopEnabled;
    }

    public void setIdleStopEnabled(Boolean idleStopEnabled) {
        this.idleStopEnabled = idleStopEnabled;
    }

    public Long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds == null ? 300L : idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(Long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public Long getIdleScanIntervalSeconds() {
        return idleScanIntervalSeconds == null ? 30L : idleScanIntervalSeconds;
    }

    public void setIdleScanIntervalSeconds(Long idleScanIntervalSeconds) {
        this.idleScanIntervalSeconds = idleScanIntervalSeconds;
    }
}
