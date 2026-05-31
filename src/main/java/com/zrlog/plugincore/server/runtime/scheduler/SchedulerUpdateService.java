package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.SchedulerUpdateRequest;
import com.zrlog.plugin.message.SchedulerUpdateResult;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;

import java.time.ZonedDateTime;

public class SchedulerUpdateService {

    static final String READ_ONLY_MESSAGE = "Scheduler updates are managed by runtime scheduler center";

    public SchedulerUpdateService(AutomationStore automationStore, CapabilityStore capabilityStore, BasicCronParser cronParser) {
    }

    public SchedulerUpdateResult update(Plugin plugin, SchedulerUpdateRequest request, ZonedDateTime now) {
        return error(READ_ONLY_MESSAGE);
    }

    private SchedulerUpdateResult error(String message) {
        SchedulerUpdateResult result = new SchedulerUpdateResult();
        result.setSuccess(false);
        result.setErrorMessage(message == null || message.trim().isEmpty() ? "Scheduler update failed" : message);
        return result;
    }
}
