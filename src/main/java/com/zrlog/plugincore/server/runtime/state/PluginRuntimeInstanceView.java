package com.zrlog.plugincore.server.runtime.state;

public class PluginRuntimeInstanceView {

    private String pluginId;
    private String pluginName;
    private String pluginVersion;
    private String pluginPreviewImageBase64;
    private String instanceId;
    private String ownerId;
    private String status;
    private String effectiveStatus;
    private String runtimeMode;
    private Long processId;
    private Boolean local;
    private Long startedAt;
    private Long readyAt;
    private Long lastActiveAt;
    private Long heartbeatAt;
    private Long leaseExpiresAt;
    private Integer activeInvocationCount;
    private String lastError;
    private Boolean processAlive;
    private Long processSampledAt;
    private Long totalCpuDurationMillis;
    private Long residentMemoryBytes;
    private Long virtualMemoryBytes;
    private Integer threadCount;
    private Long heapUsedBytes;
    private Long heapCommittedBytes;
    private Long heapMaxBytes;
    private String processErrorMessage;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public String getPluginPreviewImageBase64() {
        return pluginPreviewImageBase64;
    }

    public void setPluginPreviewImageBase64(String pluginPreviewImageBase64) {
        this.pluginPreviewImageBase64 = pluginPreviewImageBase64;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEffectiveStatus() {
        return effectiveStatus;
    }

    public void setEffectiveStatus(String effectiveStatus) {
        this.effectiveStatus = effectiveStatus;
    }

    public String getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(String runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public Long getProcessId() {
        return processId;
    }

    public void setProcessId(Long processId) {
        this.processId = processId;
    }

    public Boolean getLocal() {
        return local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Long readyAt) {
        this.readyAt = readyAt;
    }

    public Long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Long getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(Long heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }

    public Long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Long leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public Integer getActiveInvocationCount() {
        return activeInvocationCount;
    }

    public void setActiveInvocationCount(Integer activeInvocationCount) {
        this.activeInvocationCount = activeInvocationCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Boolean getProcessAlive() {
        return processAlive;
    }

    public void setProcessAlive(Boolean processAlive) {
        this.processAlive = processAlive;
    }

    public Long getProcessSampledAt() {
        return processSampledAt;
    }

    public void setProcessSampledAt(Long processSampledAt) {
        this.processSampledAt = processSampledAt;
    }

    public Long getTotalCpuDurationMillis() {
        return totalCpuDurationMillis;
    }

    public void setTotalCpuDurationMillis(Long totalCpuDurationMillis) {
        this.totalCpuDurationMillis = totalCpuDurationMillis;
    }

    public Long getResidentMemoryBytes() {
        return residentMemoryBytes;
    }

    public void setResidentMemoryBytes(Long residentMemoryBytes) {
        this.residentMemoryBytes = residentMemoryBytes;
    }

    public Long getVirtualMemoryBytes() {
        return virtualMemoryBytes;
    }

    public void setVirtualMemoryBytes(Long virtualMemoryBytes) {
        this.virtualMemoryBytes = virtualMemoryBytes;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    public Long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    public void setHeapUsedBytes(Long heapUsedBytes) {
        this.heapUsedBytes = heapUsedBytes;
    }

    public Long getHeapCommittedBytes() {
        return heapCommittedBytes;
    }

    public void setHeapCommittedBytes(Long heapCommittedBytes) {
        this.heapCommittedBytes = heapCommittedBytes;
    }

    public Long getHeapMaxBytes() {
        return heapMaxBytes;
    }

    public void setHeapMaxBytes(Long heapMaxBytes) {
        this.heapMaxBytes = heapMaxBytes;
    }

    public String getProcessErrorMessage() {
        return processErrorMessage;
    }

    public void setProcessErrorMessage(String processErrorMessage) {
        this.processErrorMessage = processErrorMessage;
    }
}
