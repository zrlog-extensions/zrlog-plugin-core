package com.zrlog.plugincore.server.runtime.scheduler;

public class SchedulerTickResult {

    private int executedCount;
    private int skippedCount;
    private int failedCount;

    public int getExecutedCount() {
        return executedCount;
    }

    public void setExecutedCount(int executedCount) {
        this.executedCount = executedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public void executed() {
        executedCount++;
    }

    public void skipped() {
        skippedCount++;
    }

    public void failed() {
        failedCount++;
    }

    public void merge(SchedulerTickResult result) {
        if (result == null) {
            return;
        }
        executedCount += result.getExecutedCount();
        skippedCount += result.getSkippedCount();
        failedCount += result.getFailedCount();
    }
}
