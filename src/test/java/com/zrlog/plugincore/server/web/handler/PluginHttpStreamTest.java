package com.zrlog.plugincore.server.web.handler;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.type.ActionType;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginHttpStreamTest {

    @Test
    public void shouldCachePluginReportedStaticAssets() {
        Plugin plugin = pluginWithCacheableStaticPaths("/static/assets/", "/static/js/main.js");

        assertTrue(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/static/assets/main.a1b2c3d4.js", ActionType.HTTP_FILE));
        assertTrue(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/static/assets/main.35820637.css", ActionType.HTTP_FILE));
        assertTrue(PluginHttpStream.shouldCachePluginStaticResource(plugin, "static/js/main.js?v=1", ActionType.HTTP_FILE));
    }

    @Test
    public void shouldNotCacheUnreportedOrDynamicPluginResponses() {
        Plugin plugin = pluginWithCacheableStaticPaths("/static/js/main.js", "/api/");

        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/static/app.js", ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/static/index.html", ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/api/status.json", ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/static/js/main.js", ActionType.HTTP_METHOD));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, "/pwa-sw.js", ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(plugin, null, ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(new Plugin(), "/static/js/main.js", ActionType.HTTP_FILE));
        assertFalse(PluginHttpStream.shouldCachePluginStaticResource(null, "/static/js/main.js", ActionType.HTTP_FILE));
    }

    private static Plugin pluginWithCacheableStaticPaths(String... paths) {
        Plugin plugin = new Plugin();
        plugin.setCacheableStaticPaths(new LinkedHashSet<>(Arrays.asList(paths)));
        return plugin;
    }
}
