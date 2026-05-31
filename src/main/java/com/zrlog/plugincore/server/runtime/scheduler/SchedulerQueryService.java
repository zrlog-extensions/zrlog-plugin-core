package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.message.SchedulerQueryRequest;
import com.zrlog.plugin.message.SchedulerQueryResult;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;

import java.util.Objects;
import java.util.Optional;

public class SchedulerQueryService {

    private final AutomationStore automationStore;
    private final CapabilityStore capabilityStore;

    public SchedulerQueryService(AutomationStore automationStore, CapabilityStore capabilityStore) {
        this.automationStore = automationStore;
        this.capabilityStore = capabilityStore;
    }

    public SchedulerQueryResult query(Plugin plugin, SchedulerQueryRequest request) {
        if (plugin == null || isBlank(plugin.getId())) {
            return error("Plugin session is invalid");
        }
        if (request == null || isBlank(request.getCapabilityKey())) {
            return error("Capability key is empty");
        }
        Optional<PluginCapability> capability = capabilityStore.find(plugin.getId(), request.getCapabilityKey());
        if (!capability.isPresent()) {
            return error("Capability not found: " + request.getCapabilityKey());
        }
        if (!Objects.equals("scheduled", capability.get().getType())
                || capability.get().getExposure() == null
                || !capability.get().getExposure().contains("scheduler")) {
            return error("Capability is not exposed to scheduler");
        }
        for (PluginAutomation automation : automationStore.list()) {
            if (Objects.equals(plugin.getId(), automation.getPluginId())
                    && Objects.equals(request.getCapabilityKey(), automation.getCapabilityKey())) {
                return success(automation);
            }
        }
        return error("Automation not found");
    }

    private SchedulerQueryResult success(PluginAutomation automation) {
        SchedulerQueryResult result = new SchedulerQueryResult();
        result.setSuccess(true);
        result.setAutomationId(automation.getId());
        result.setCapabilityKey(automation.getCapabilityKey());
        result.setName(automation.getName());
        result.setCron(automation.getCron());
        result.setTimezone(automation.getTimezone());
        result.setEnabled(automation.getEnabled());
        result.setNextRunAt(automation.getNextRunAt() == null ? null : String.valueOf(automation.getNextRunAt()));
        result.setLastRunAt(automation.getLastRunAt() == null ? null : String.valueOf(automation.getLastRunAt()));
        return result;
    }

    private SchedulerQueryResult error(String message) {
        SchedulerQueryResult result = new SchedulerQueryResult();
        result.setSuccess(false);
        result.setErrorMessage(message == null || message.trim().isEmpty() ? "Scheduler query failed" : message);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
