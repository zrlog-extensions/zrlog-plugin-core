package com.zrlog.plugincore.server.type;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginStatusTest {

    @Test
    public void shouldMapRuntimeStatusToPluginStatus() {
        assertEquals(PluginStatus.READY, PluginStatus.fromRuntimeStatus("ready"));
        assertEquals(PluginStatus.EXECUTING, PluginStatus.fromRuntimeStatus("executing"));
        assertEquals(PluginStatus.STOPPED, PluginStatus.fromRuntimeStatus("stopped"));
        assertEquals("ready", PluginStatus.lifecycleRuntimeStatus("executing"));
    }

    @Test
    public void shouldKeepLegacyStatusSemantics() {
        assertTrue(PluginStatus.START.isStarted());
        assertFalse(PluginStatus.STOP.isStarted());
        assertEquals("ready", PluginStatus.START.runtimeStatus());
        assertEquals("stopped", PluginStatus.STOP.runtimeStatus());
    }
}
