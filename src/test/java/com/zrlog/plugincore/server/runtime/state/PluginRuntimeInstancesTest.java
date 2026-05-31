package com.zrlog.plugincore.server.runtime.state;

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
}
