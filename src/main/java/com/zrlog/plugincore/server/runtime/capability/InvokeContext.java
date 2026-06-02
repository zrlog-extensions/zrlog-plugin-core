package com.zrlog.plugincore.server.runtime.capability;

public class InvokeContext {

    private String source;
    private String userId;
    private String requestId;
    private String traceId;
    private Integer timeoutSeconds;
    private boolean auditRequired;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isAuditRequired() {
        return auditRequired;
    }

    public void setAuditRequired(boolean auditRequired) {
        this.auditRequired = auditRequired;
    }
}
