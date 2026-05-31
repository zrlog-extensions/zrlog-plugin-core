package com.zrlog.plugincore.server.runtime.notification;

import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.NotificationRequest;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.capability.CapabilityInvoker;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.InvokeContext;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NotificationRuntimeTest {

    @Test
    public void shouldPublishToEmailProvider() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(emailProvider());
        NotificationDeliveryStore deliveryStore = new NotificationDeliveryStore(kvStore);

        NotificationPublishResult result = runtime(capabilityStore, deliveryStore, successInvoker())
                .publish(request("email"));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, deliveryStore.list().size());
        assertEquals("success", deliveryStore.list().get(0).getStatus());
        assertEquals("email-plugin", deliveryStore.list().get(0).getProviderPluginId());
    }

    @Test
    public void shouldForwardStandardNotificationFieldsToProvider() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        capabilityStore.register(emailProvider());
        NotificationDeliveryStore deliveryStore = new NotificationDeliveryStore(kvStore);

        runtime(capabilityStore, deliveryStore, new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                assertEquals("email-plugin", pluginId);
                assertEquals("notification.email.send", capabilityKey);
                assertEquals("reminder", payload.get("sourcePluginId"));
                assertEquals("reminder.due", payload.get("eventType"));
                assertEquals("reminder", payload.get("notificationType"));
                assertEquals("email", payload.get("channel"));
                assertEquals("Reminder", payload.get("title"));
                assertEquals("提醒内容", payload.get("content"));
                assertEquals("warning", payload.get("level"));
                assertEquals("1", ((Map) payload.get("payload")).get("id"));
                CapabilityInvokeResult result = new CapabilityInvokeResult();
                result.setSuccess(true);
                return result;
            }
        }).publish(request("email"));
    }

    @Test
    public void shouldWriteFailureForUnknownChannel() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        NotificationDeliveryStore deliveryStore = new NotificationDeliveryStore(kvStore);

        NotificationPublishResult result = runtime(capabilityStore, deliveryStore, successInvoker())
                .publish(request("email"));

        assertEquals(1, result.getFailedCount());
        assertEquals("Notification channel provider not found", deliveryStore.list().get(0).getErrorMessage());
    }

    private NotificationRuntime runtime(CapabilityStore capabilityStore,
                                        NotificationDeliveryStore deliveryStore,
                                        CapabilityInvoker invoker) {
        return new NotificationRuntime(capabilityStore, deliveryStore, new NotificationProviderResolver(), new NotificationSetting(), invoker);
    }

    private CapabilityInvoker successInvoker() {
        return new CapabilityInvoker() {
            @Override
            public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
                CapabilityInvokeResult result = new CapabilityInvokeResult();
                result.setSuccess(true);
                return result;
            }
        };
    }

    private NotificationRequest request(String channel) {
        NotificationRequest request = new NotificationRequest();
        request.setSourcePluginId("reminder");
        request.setSourcePluginName("reminder");
        request.setSourceCapabilityKey("reminder.scanDueTasks");
        request.setEventType("reminder.due");
        request.setNotificationType("reminder");
        request.setChannels(Collections.singletonList(channel));
        request.setTitle("Reminder");
        request.setContent("提醒内容");
        request.setLevel("warning");
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("id", "1");
        request.setPayload(payload);
        return request;
    }

    private PluginCapability emailProvider() {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId("email-plugin");
        capability.setPluginName("email");
        capability.setKey("notification.email.send");
        capability.setType("notification_channel");
        capability.setExposure(Arrays.asList("notification"));
        capability.setChannel("email");
        capability.setEnabled(Boolean.TRUE);
        return capability;
    }
}
