package com.zrlog.plugincore.server.runtime.pwa;

import com.zrlog.plugin.message.Plugin;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PluginPwaResourcesTest {

    @Test
    public void shouldBuildScopedManifestFromExistingPluginMetadata() {
        Plugin plugin = plugin();

        Map<String, Object> manifest = new PluginPwaResources().manifest(plugin, "/admin/plugins/reminder/");

        assertEquals("/admin/plugins/reminder/", manifest.get("id"));
        assertEquals("Reminder", manifest.get("name"));
        assertEquals("reminder", manifest.get("short_name"));
        assertEquals("Create and manage reminders", manifest.get("description"));
        assertEquals("/admin/plugins/reminder/", manifest.get("start_url"));
        assertEquals("/admin/plugins/reminder/", manifest.get("scope"));
        assertEquals("standalone", manifest.get("display"));
        List icons = (List) manifest.get("icons");
        assertEquals("/admin/plugins/reminder/pwa-icon", ((Map) icons.get(0)).get("src"));
        assertEquals("any", ((Map) icons.get(0)).get("sizes"));
        assertEquals("image/svg+xml", ((Map) icons.get(0)).get("type"));
    }

    @Test
    public void shouldDecodePreviewImageDataUrlForIconEndpoint() {
        byte[] svg = "<svg/>".getBytes(StandardCharsets.UTF_8);
        PluginPwaResources.PreviewIcon icon = PluginPwaResources.previewIcon(
                "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg));

        assertNotNull(icon);
        assertEquals("image/svg+xml", icon.getContentType());
        assertArrayEquals(svg, icon.getBytes());
    }

    @Test
    public void shouldDetectStandardPwaResourceActions() {
        assertTrue(PluginPwaResources.isPwaResource("/manifest.webmanifest"));
        assertTrue(PluginPwaResources.isPwaResource("/manifest.json"));
        assertTrue(PluginPwaResources.isPwaResource("/pwa-sw.js"));
        assertTrue(PluginPwaResources.isPwaResource("/pwa-icon"));
        assertFalse(PluginPwaResources.isPwaResource("/static/app.js"));
    }

    @Test
    public void shouldResolvePluginBasePathFromRequestUri() {
        assertEquals("/admin/plugins/reminder/",
                PluginPwaResources.pluginBasePath("/admin/plugins/reminder/manifest.webmanifest", "/manifest.webmanifest", "reminder"));
        assertEquals("/p/reminder/",
                PluginPwaResources.pluginBasePath("/p/reminder/pwa-sw.js?v=1", "/pwa-sw.js", "reminder"));
    }

    private Plugin plugin() {
        Plugin plugin = new Plugin();
        plugin.setShortName("reminder");
        plugin.setName("Reminder");
        plugin.setDesc("Create and manage reminders");
        plugin.setPreviewImageBase64("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg/>".getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }
}
