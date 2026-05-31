package com.zrlog.plugincore.server.controller;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.Application;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuntimeApiControllerTest {

    @Test
    public void shouldNotLoadRuntimeStatesInNativeAgentMode() {
        Boolean previous = Application.nativeAgent;
        try {
            Application.nativeAgent = true;

            assertTrue(RuntimeApiController.runtimeStatesForCurrentMode().isEmpty());
        } finally {
            Application.nativeAgent = previous;
        }
    }

    @Test
    public void shouldBuildPluginAutomationTargetLabel() {
        Plugin plugin = new Plugin();
        plugin.setName("RSS 订阅源");
        PluginCapability capability = new PluginCapability();
        capability.setPluginId("rss-2");
        capability.setPluginName("RSS 订阅源");
        capability.setKey("rss.refreshFeed");
        capability.setLabel("刷新 RSS 文件");

        assertEquals("RSS 订阅源 / 刷新 RSS 文件",
                RuntimeApiController.automationTargetLabel("default:rss-2:rss.refreshFeed",
                        "rss-2", "rss.refreshFeed", "自定义任务名", plugin, capability));
    }

    @Test
    public void shouldBuildSystemAutomationTargetLabel() {
        assertEquals("系统任务 / 运行态维护",
                RuntimeApiController.automationTargetLabel("system:plugin-runtime-maintenance",
                        "__system__", "plugin.runtime.maintenance", "运行态维护", null, null));
    }
}
