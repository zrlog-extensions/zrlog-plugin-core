package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.message.SchedulerUpdateRequest;
import com.zrlog.plugin.message.SchedulerUpdateResult;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SchedulerUpdateServiceTest {

    @Test
    public void shouldRejectSchedulerUpdateWithoutMutatingAutomation() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(capability("plugin-a", "statistics.dailyReport", "生成每日站点数据"));
        AutomationStore automationStore = new AutomationStore(kvStore);
        automationStore.saveAll(Collections.singletonList(automation("a1", "plugin-a", "statistics.dailyReport")));

        SchedulerUpdateResult result = new SchedulerUpdateService(automationStore, capabilityStore, new BasicCronParser())
                .update(plugin("plugin-a"), request("statistics.dailyReport", "*/10 * * * *", Boolean.FALSE), null);

        assertFalse(result.isSuccess());
        assertEquals(SchedulerUpdateService.READ_ONLY_MESSAGE, result.getErrorMessage());
        assertEquals(1, automationStore.list().size());
        assertEquals("*/5 * * * *", automationStore.list().get(0).getCron());
        assertEquals(Boolean.TRUE, automationStore.list().get(0).getEnabled());
    }

    private Plugin plugin(String pluginId) {
        Plugin plugin = new Plugin();
        plugin.setId(pluginId);
        plugin.setShortName(pluginId);
        return plugin;
    }

    private SchedulerUpdateRequest request(String capabilityKey, String cron, Boolean enabled) {
        SchedulerUpdateRequest request = new SchedulerUpdateRequest();
        request.setCapabilityKey(capabilityKey);
        request.setCron(cron);
        request.setEnabled(enabled);
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
        automation.setEnabled(Boolean.TRUE);
        return automation;
    }
}
