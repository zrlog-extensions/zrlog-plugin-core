package com.zrlog.plugincore.server.web.controller;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.runtime.notification.NotificationDelivery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuntimeApiControllerTest {

    @Test
    public void shouldNotLoadRuntimeStatesInNativeAgentMode() {
        RunType previous = RunConstants.runType;
        try {
            RunConstants.runType = RunType.AGENT;

            assertTrue(RuntimeApiController.runtimeStatesForCurrentMode().isEmpty());
        } finally {
            RunConstants.runType = previous;
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

    @Test
    public void shouldFindLatestNotificationDeliveryByProvider() {
        NotificationDelivery oldDelivery = delivery("email", "email-plugin", "notification.email.send", "error", 100L);
        NotificationDelivery newDelivery = delivery("email", "email-plugin", "notification.email.send", "success", 200L);
        NotificationDelivery otherDelivery = delivery("webhook", "webhook-plugin", "notification.webhook.send", "success", 300L);

        Map<String, NotificationDelivery> latest = RuntimeApiController.latestNotificationDeliveryByProvider(
                Arrays.asList(oldDelivery, otherDelivery, newDelivery));

        assertEquals(2, latest.size());
        assertEquals("success", latest.get("email\nemail-plugin\nnotification.email.send").getStatus());
        assertEquals(Long.valueOf(200L), latest.get("email\nemail-plugin\nnotification.email.send").getCreatedAt());
    }

    private NotificationDelivery delivery(String channel, String pluginId, String capabilityKey, String status, Long createdAt) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setChannel(channel);
        delivery.setProviderPluginId(pluginId);
        delivery.setCapabilityKey(capabilityKey);
        delivery.setStatus(status);
        delivery.setCreatedAt(createdAt);
        return delivery;
    }
}
