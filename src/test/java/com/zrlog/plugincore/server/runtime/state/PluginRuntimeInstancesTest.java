package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugin.message.Plugin;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PluginRuntimeInstancesTest {

    @Test
    public void shouldBuildProcessInstanceIdFromHostAndPid() {
        assertEquals("blog-node-42", PluginRuntimeInstances.hostProcessId("Blog Node", 42L));
    }

    @Test
    public void shouldBuildSessionInstanceIdFromCurrentOwnerPrefix() {
        String first = PluginRuntimeInstances.newInstanceId();
        String second = PluginRuntimeInstances.newInstanceId();

        assertTrue(first.startsWith(PluginRuntimeInstances.currentInstanceId() + "/session-"));
        assertTrue(second.startsWith(PluginRuntimeInstances.currentInstanceId() + "/session-"));
    }

    @Test
    public void shouldNormalizeBlankHostName() {
        assertEquals("localhost-7", PluginRuntimeInstances.hostProcessId("   ", 7L));
    }

    @Test
    public void shouldExposePluginVersionOnRuntimeInstanceView() {
        Plugin plugin = new Plugin();
        plugin.setId("plugin-a");
        plugin.setName("Reminder");
        plugin.setShortName("reminder");
        plugin.setVersion("1.2.3");
        PluginRuntimeInstanceState instance = new PluginRuntimeInstanceState();
        instance.setInstanceId("instance-a");
        instance.setStatus("ready");
        instance.setRuntimeMode("process");

        PluginRuntimeInstanceView view = PluginRuntimeStates.instanceView(plugin, "Reminder", instance);

        assertEquals("1.2.3", view.getPluginVersion());
    }
}
