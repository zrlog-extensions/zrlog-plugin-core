package com.zrlog.plugincore.server.runtime.notification;

public class NotificationPublishResult {

    private int successCount;
    private int failedCount;

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public void success() {
        successCount++;
    }

    public void failed() {
        failedCount++;
    }
}
