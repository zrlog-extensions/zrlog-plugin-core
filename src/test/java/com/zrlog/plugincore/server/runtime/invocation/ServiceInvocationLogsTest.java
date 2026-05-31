package com.zrlog.plugincore.server.runtime.invocation;

import com.zrlog.plugincore.server.runtime.capability.RuntimeSources;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ServiceInvocationLogsTest {

    @Test
    public void shouldCreateSuccessLogForServiceInvocation() {
        CapabilityInvocationLog log = ServiceInvocationLogs.create(
                "plugin-a", "uploadService", "request-1", "trace-1", 100L, 125L, null);

        assertEquals("plugin-a", log.getPluginId());
        assertEquals("uploadService", log.getCapabilityKey());
        assertEquals(RuntimeSources.INTERNAL, log.getSource());
        assertEquals("request-1", log.getRequestId());
        assertEquals("trace-1", log.getTraceId());
        assertEquals("success", log.getStatus());
        assertEquals(Long.valueOf(25L), log.getDurationMs());
        assertNull(log.getErrorMessage());
    }

    @Test
    public void shouldCreateErrorLogForServiceInvocation() {
        CapabilityInvocationLog log = ServiceInvocationLogs.create(
                "plugin-a", "uploadService", "request-1", null, 100L, 110L, "service response error");

        assertEquals("error", log.getStatus());
        assertEquals("service response error", log.getErrorMessage());
    }
}
