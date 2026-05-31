package com.zrlog.plugincore.server.runtime.scheduler;

public class SchedulerTickService {

    private final SchedulerSetting schedulerSetting;
    private final SchedulerRuntime schedulerRuntime;

    public SchedulerTickService(SchedulerSetting schedulerSetting, SchedulerRuntime schedulerRuntime) {
        this.schedulerSetting = schedulerSetting;
        this.schedulerRuntime = schedulerRuntime;
    }

    public SchedulerTickResult tick(java.time.ZonedDateTime now) {
        if (schedulerSetting != null) {
            schedulerSetting.ensureDefaultProvider();
        }
        return schedulerRuntime.tick(now);
    }

    public PluginAutomationRun runNow(String automationId, java.time.ZonedDateTime now) {
        if (schedulerSetting != null) {
            schedulerSetting.ensureDefaultProvider();
        }
        return schedulerRuntime.runNow(automationId, now);
    }
}
