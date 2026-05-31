package com.zrlog.plugincore.server.runtime.event;

import com.zrlog.plugin.RuntimeEvents;
import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.capability.CapabilityInvoker;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.InvokeContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuntimeEventRuntimeTest {

    @Test
    public void shouldInvokeSubscribedHandlersWithRuntimeEventContext() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(handler("cache-plugin", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE));
        final List<String> calls = new ArrayList<>();

        RuntimeEventPublishResult result = runtime(capabilityStore, new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                calls.add(pluginId + ":" + capabilityKey);
                assertEquals("cache-plugin", pluginId);
                assertEquals("plugin.cache.refresh", capabilityKey);
                assertEquals(RuntimeEventRuntime.SOURCE, context.getSource());
                assertEquals("request-1", context.getRequestId());
                assertEquals(RuntimeEvents.REFRESH_CACHE, payload.get("eventType"));
                assertEquals("plugin-core", payload.get("source"));
                assertEquals("REFRESH_CACHE", ((Map) payload.get("payload")).get("actionType"));
                CapabilityInvokeResult result = new CapabilityInvokeResult();
                result.setSuccess(true);
                return result;
            }
        }).publish(refreshCacheRequest());

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getHandlerCount());
        assertTrue(result.getHandlerPluginIds().contains("cache-plugin"));
        assertEquals(Collections.singletonList("cache-plugin:plugin.cache.refresh"), calls);
    }

    @Test
    public void shouldRecordFailedHandlerResult() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(handler("cache-plugin", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE));

        RuntimeEventPublishResult result = runtime(capabilityStore, new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                CapabilityInvokeResult result = new CapabilityInvokeResult();
                result.setSuccess(false);
                result.setErrorMessage("failed");
                return result;
            }
        }).publish(refreshCacheRequest());

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getHandlerCount());
        assertTrue(result.getHandlerPluginIds().contains("cache-plugin"));
    }

    @Test
    public void shouldReuseGeneratedRequestIdForAllHandlers() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(handler("cache-a", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE));
        capabilityStore.register(handler("cache-b", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE));
        RuntimeEventRequest request = refreshCacheRequest();
        request.setRequestId(null);
        final List<String> contextRequestIds = new ArrayList<String>();
        final List<String> payloadRequestIds = new ArrayList<String>();

        RuntimeEventPublishResult result = runtime(capabilityStore, new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                contextRequestIds.add(context.getRequestId());
                payloadRequestIds.add((String) payload.get("requestId"));
                CapabilityInvokeResult result = new CapabilityInvokeResult();
                result.setSuccess(true);
                return result;
            }
        }).publish(request);

        assertEquals(2, result.getSuccessCount());
        assertNotNull(request.getRequestId());
        assertFalse(request.getRequestId().trim().isEmpty());
        assertEquals(Arrays.asList(request.getRequestId(), request.getRequestId()), contextRequestIds);
        assertEquals(contextRequestIds, payloadRequestIds);
    }

    @Test
    public void shouldNotInvokeWhenNoHandlerSubscribed() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(handler("cache-plugin", "plugin.cache.refresh", "other.event"));

        RuntimeEventPublishResult result = runtime(capabilityStore, neverInvoker()).publish(refreshCacheRequest());

        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(0, result.getHandlerCount());
        assertFalse(result.getHandlerPluginIds().contains("cache-plugin"));
    }

    @Test
    public void shouldReturnFailureForEmptyEventType() {
        RuntimeEventRequest request = new RuntimeEventRequest();

        RuntimeEventPublishResult result = runtime(new CapabilityStore(new InMemoryRuntimeKvStore()), neverInvoker()).publish(request);

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(0, result.getHandlerCount());
    }

    private RuntimeEventRuntime runtime(CapabilityStore capabilityStore, CapabilityInvoker invoker) {
        return new RuntimeEventRuntime(capabilityStore, new RuntimeEventHandlerResolver(), invoker);
    }

    private RuntimeEventRequest refreshCacheRequest() {
        RuntimeEventRequest request = new RuntimeEventRequest();
        request.setEventType(RuntimeEvents.REFRESH_CACHE);
        request.setAliases(Arrays.asList(RuntimeEvents.LEGACY_REFRESH_CACHE, "REFRESH_CACHE"));
        request.setSource("plugin-core");
        request.setRequestId("request-1");
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("actionType", "REFRESH_CACHE");
        request.setPayload(payload);
        return request;
    }

    private PluginCapability handler(String pluginId, String key, String channel) {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginId);
        capability.setKey(key);
        capability.setType("event_handler");
        capability.setExposure(Collections.singletonList("runtime_event"));
        capability.setChannel(channel);
        capability.setEnabled(Boolean.TRUE);
        return capability;
    }

    private CapabilityInvoker neverInvoker() {
        return new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                throw new AssertionError("delegate should not be called");
            }
        };
    }
}
