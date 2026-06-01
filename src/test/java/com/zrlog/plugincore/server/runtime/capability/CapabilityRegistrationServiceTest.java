package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CapabilityRegistrationServiceTest {

    @Test
    public void shouldRegisterExplicitCapabilitiesFromInitPayload() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();

        service.registerCapabilitiesFromInitPayload(plugin, "{"
                + "\"capabilities\":[{"
                + "\"key\":\"reminder.scanDueTasks\","
                + "\"type\":\"scheduled\","
                + "\"exposure\":[\"scheduler\"],"
                + "\"defaultCron\":\"*/1 * * * *\","
                + "\"timezone\":\"system\""
                + "}]"
                + "}");

        assertEquals(1, store.listAll().size());
        assertEquals("plugin-a", store.listAll().get(0).getPluginId());
        assertEquals("待办提醒", store.listAll().get(0).getPluginName());
        assertEquals("*/1 * * * *", store.listAll().get(0).getDefaultCron());
        assertEquals("system", store.listAll().get(0).getTimezone());
    }

    @Test
    public void shouldGenerateLegacyViewWithoutPersistingIt() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();
        plugin.getServices().add("legacy.validService");
        plugin.getServices().add("run");

        assertEquals(1, service.legacyCapabilities(plugin).size());
        assertTrue(store.listAll().isEmpty());
    }

    @Test
    public void shouldRegisterCapabilitiesFromPluginObjectWhenNativePayloadMissesArray() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();
        plugin.getCapabilities().add(capability("reminder.scanDueTasks", "scheduled", "scheduler", null));
        plugin.getCapabilities().add(capability("email.send", "notification_channel", "notification", "email"));

        service.registerCapabilitiesFromInitPayload(plugin, "{}");

        assertEquals(2, store.listAll().size());
        assertEquals("plugin-a", store.listAll().get(0).getPluginId());
        assertEquals("待办提醒", store.listAll().get(0).getPluginName());
        assertEquals("scheduled", store.listAll().get(0).getType());
        assertEquals("notification_channel", store.listAll().get(1).getType());
        assertEquals("email", store.listAll().get(1).getChannel());
    }

    @Test
    public void shouldPersistLegacyCapabilitiesWhenCommonCapabilitiesAreUnavailable() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();
        plugin.getServices().add("legacy.validService");
        plugin.getServices().add("run");

        service.registerCapabilitiesFromInitPayload(plugin, "{}");

        assertEquals(1, store.listAll().size());
        assertEquals("legacy.validService", store.listAll().get(0).getKey());
        assertTrue(store.listAll().get(0).getLegacy());
    }

    @Test
    public void shouldReplaceStaleCapabilitiesFromSamePlugin() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();

        service.registerCapabilitiesFromInitPayload(plugin, payload("reminder.oldTask"));
        service.registerCapabilitiesFromInitPayload(plugin, payload("reminder.newTask"));

        assertEquals(1, store.listAll().size());
        assertEquals("reminder.newTask", store.listAll().get(0).getKey());
    }

    @Test
    public void shouldReplaceStaleCapabilitiesFromSamePluginName() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin oldPlugin = plugin();
        oldPlugin.setId("plugin-old");
        Plugin newPlugin = plugin();
        newPlugin.setId("plugin-new");

        service.registerCapabilitiesFromInitPayload(oldPlugin, payload("reminder.oldTask"));
        service.registerCapabilitiesFromInitPayload(newPlugin, payload("reminder.newTask"));

        assertEquals(1, store.listAll().size());
        assertEquals("plugin-new", store.listAll().get(0).getPluginId());
        assertEquals("reminder.newTask", store.listAll().get(0).getKey());
    }

    @Test
    public void shouldKeepServiceNameFromCapabilityPayload() {
        CapabilityStore store = new CapabilityStore(new InMemoryRuntimeKvStore());
        CapabilityRegistrationService service = new CapabilityRegistrationService(store);
        Plugin plugin = plugin();
        plugin.getServices().add("uploadService");

        service.registerCapabilitiesFromInitPayload(plugin, "{"
                + "\"capabilities\":[{"
                + "\"key\":\"qiniu.upload\","
                + "\"serviceName\":\"uploadService\","
                + "\"type\":\"service\","
                + "\"exposure\":[\"internal\"]"
                + "}]"
                + "}");

        assertEquals("uploadService", store.listAll().get(0).getServiceName());
    }

    private Plugin plugin() {
        Plugin plugin = new Plugin();
        plugin.setId("plugin-a");
        plugin.setShortName("reminder");
        plugin.setName("待办提醒");
        return plugin;
    }

    private PluginCapability capability(String key, String type, String exposure, String channel) {
        PluginCapability capability = new PluginCapability();
        capability.setKey(key);
        capability.setType(type);
        capability.setExposure(Arrays.asList(exposure));
        capability.setChannel(channel);
        capability.setEnabled(Boolean.TRUE);
        return capability;
    }

    private String payload(String key) {
        return "{"
                + "\"capabilities\":[{"
                + "\"key\":\"" + key + "\","
                + "\"type\":\"scheduled\","
                + "\"exposure\":[\"scheduler\"]"
                + "}]"
                + "}";
    }
}
