package com.zrlog.plugincore.server.runtime.scheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SchedulerExternalEndpointTest {

    @Test
    public void shouldUseConfiguredHostBeforeFallback() {
        assertEquals("https://blog.example.com" + SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH,
                SchedulerExternalEndpoint.tickUrl("blog.example.com/", "http://fallback.example.com"));
    }

    @Test
    public void shouldFallbackToHomeUrlWhenConfiguredHostBlank() {
        assertEquals("http://fallback.example.com" + SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH,
                SchedulerExternalEndpoint.tickUrl("  ", "http://fallback.example.com/"));
    }

    @Test
    public void shouldReturnPathWhenNoHostAvailable() {
        assertEquals(SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH,
                SchedulerExternalEndpoint.tickUrl(null, ""));
    }

    @Test
    public void shouldIgnoreProtocolOnlyHost() {
        assertEquals(SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH,
                SchedulerExternalEndpoint.tickUrl("http:", ""));
    }

    @Test
    public void shouldRepairSingleSlashProtocolHost() {
        assertEquals("http://blog.example.com" + SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH,
                SchedulerExternalEndpoint.tickUrl("http:/blog.example.com/", ""));
    }
}
