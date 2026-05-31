package com.zrlog.plugincore.server.runtime.event;

import com.zrlog.plugin.RuntimeEvents;
import com.zrlog.plugin.message.PluginCapability;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuntimeEventHandlerResolverTest {

    @Test
    public void shouldResolveRuntimeEventHandlerByEventType() {
        RuntimeEventHandlerResolver resolver = new RuntimeEventHandlerResolver();
        RuntimeEventRequest request = request(RuntimeEvents.REFRESH_CACHE);

        List<PluginCapability> handlers = resolver.resolve(request, Arrays.asList(
                handler("cache-b", "Cache B", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE),
                handler("cache-a", "Cache A", "plugin.cache.refresh", RuntimeEvents.REFRESH_CACHE)
        ));

        assertEquals(2, handlers.size());
        assertEquals("cache-a", handlers.get(0).getPluginId());
        assertEquals("cache-b", handlers.get(1).getPluginId());
    }

    @Test
    public void shouldMatchAliasesAndCommaSeparatedSubscriptions() {
        RuntimeEventHandlerResolver resolver = new RuntimeEventHandlerResolver();
        RuntimeEventRequest request = request(RuntimeEvents.REFRESH_CACHE);
        request.setAliases(Arrays.asList(RuntimeEvents.LEGACY_REFRESH_CACHE, "REFRESH_CACHE"));

        List<PluginCapability> handlers = resolver.resolve(request, Collections.singletonList(
                handler("cache", "Cache", "plugin.cache.refresh", "article.changed, refresh_cache")
        ));

        assertEquals(1, handlers.size());
        assertEquals("cache", handlers.get(0).getPluginId());
    }

    @Test
    public void shouldIgnoreWrongTypeExposureAndDisabledHandlers() {
        RuntimeEventHandlerResolver resolver = new RuntimeEventHandlerResolver();

        List<PluginCapability> handlers = resolver.resolve(request(RuntimeEvents.REFRESH_CACHE), Arrays.asList(
                capability("service", "Service", "plugin.cache.refresh", "service", "runtime_event", RuntimeEvents.REFRESH_CACHE, true),
                capability("internal", "Internal", "plugin.cache.refresh", "event_handler", "internal", RuntimeEvents.REFRESH_CACHE, true),
                capability("disabled", "Disabled", "plugin.cache.refresh", "event_handler", "runtime_event", RuntimeEvents.REFRESH_CACHE, false)
        ));

        assertTrue(handlers.isEmpty());
    }

    private RuntimeEventRequest request(String eventType) {
        RuntimeEventRequest request = new RuntimeEventRequest();
        request.setEventType(eventType);
        return request;
    }

    private PluginCapability handler(String pluginId, String pluginName, String key, String channel) {
        return capability(pluginId, pluginName, key, "event_handler", "runtime_event", channel, true);
    }

    private PluginCapability capability(String pluginId,
                                        String pluginName,
                                        String key,
                                        String type,
                                        String exposure,
                                        String channel,
                                        boolean enabled) {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginName);
        capability.setKey(key);
        capability.setType(type);
        capability.setExposure(Collections.singletonList(exposure));
        capability.setChannel(channel);
        capability.setEnabled(enabled);
        return capability;
    }
}
