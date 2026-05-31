package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.message.SchedulerQueryRequest;
import com.zrlog.plugin.message.SchedulerQueryResult;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchedulerQueryServiceTest {

    @Test
    public void shouldQueryOwnScheduledAutomation() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(capability("plugin-a", "statistics.dailyReport", "生成每日站点数据"));
        AutomationStore automationStore = new AutomationStore(kvStore);
        PluginAutomation automation = automation("a1", "plugin-a", "statistics.dailyReport");
        automation.setNextRunAt(1800000L);
        automation.setLastRunAt(1200000L);
        automationStore.saveAll(Collections.singletonList(automation));

        SchedulerQueryResult result = new SchedulerQueryService(automationStore, capabilityStore)
                .query(plugin("plugin-a"), request("statistics.dailyReport"));

        assertTrue(result.isSuccess());
        assertEquals("a1", result.getAutomationId());
        assertEquals("statistics.dailyReport", result.getCapabilityKey());
        assertEquals("*/5 * * * *", result.getCron());
        assertEquals(Boolean.TRUE, result.getEnabled());
        assertEquals("1800000", result.getNextRunAt());
        assertEquals("1200000", result.getLastRunAt());
    }

    @Test
    public void shouldRejectQueryingAnotherPluginSchedule() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(capability("plugin-b", "statistics.dailyReport", "生成每日站点数据"));
        AutomationStore automationStore = new AutomationStore(kvStore);
        automationStore.saveAll(Collections.singletonList(automation("a1", "plugin-b", "statistics.dailyReport")));

        SchedulerQueryResult result = new SchedulerQueryService(automationStore, capabilityStore)
                .query(plugin("plugin-a"), request("statistics.dailyReport"));

        assertFalse(result.isSuccess());
        assertEquals("Capability not found: statistics.dailyReport", result.getErrorMessage());
    }

    private Plugin plugin(String pluginId) {
        Plugin plugin = new Plugin();
        plugin.setId(pluginId);
        plugin.setShortName(pluginId);
        return plugin;
    }

    private SchedulerQueryRequest request(String capabilityKey) {
        SchedulerQueryRequest request = new SchedulerQueryRequest();
        request.setCapabilityKey(capabilityKey);
        return request;
    }

    private PluginCapability capability(String pluginId, String key, String label) {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginId);
        capability.setKey(key);
        capability.setLabel(label);
        capability.setType("scheduled");
        capability.setExposure(Arrays.asList("scheduler"));
        capability.setEnabled(Boolean.TRUE);
        capability.setDefaultCron("*/5 * * * *");
        return capability;
    }

    private PluginAutomation automation(String id, String pluginId, String capabilityKey) {
        PluginAutomation automation = new PluginAutomation();
        automation.setId(id);
        automation.setName("Daily report");
        automation.setPluginId(pluginId);
        automation.setCapabilityKey(capabilityKey);
        automation.setCron("*/5 * * * *");
        automation.setTimezone("system");
        automation.setEnabled(Boolean.TRUE);
        return automation;
    }
}
