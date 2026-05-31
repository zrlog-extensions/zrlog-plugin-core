package com.zrlog.plugincore.server.runtime.notification;

import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.NotificationRequest;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.capability.CapabilityInvoker;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.InvokeContext;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NotificationRuntime {

    private final CapabilityStore capabilityStore;
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationProviderResolver providerResolver;
    private final NotificationSetting notificationSetting;
    private final CapabilityInvoker capabilityInvoker;

    public NotificationRuntime(CapabilityStore capabilityStore,
                               NotificationDeliveryStore deliveryStore,
                               NotificationProviderResolver providerResolver,
                               NotificationSetting notificationSetting,
                               CapabilityInvoker capabilityInvoker) {
        this.capabilityStore = capabilityStore;
        this.deliveryStore = deliveryStore;
        this.providerResolver = providerResolver;
        this.notificationSetting = notificationSetting;
        this.capabilityInvoker = capabilityInvoker;
    }

    public NotificationPublishResult publish(NotificationRequest request) {
        NotificationPublishResult result = new NotificationPublishResult();
        if (request.getChannels() == null || request.getChannels().isEmpty()) {
            result.failed();
            deliveryStore.append(failedDelivery(null, null, "No notification channels"));
            return result;
        }
        for (String channel : request.getChannels()) {
            Optional<PluginCapability> provider = providerResolver.resolve(channel, capabilityStore.listAll(), notificationSetting);
            if (!provider.isPresent()) {
                result.failed();
                deliveryStore.append(failedDelivery(channel, null, "Notification channel provider not found"));
                continue;
            }
            NotificationDelivery delivery = invokeProvider(channel, provider.get(), request);
            deliveryStore.append(delivery);
            if ("success".equals(delivery.getStatus())) {
                result.success();
            } else {
                result.failed();
            }
        }
        return result;
    }

    private NotificationDelivery invokeProvider(String channel, PluginCapability provider, NotificationRequest request) {
        InvokeContext context = new InvokeContext();
        context.setSource("notification");
        context.setRequestId(request.getRequestId() == null ? UUID.randomUUID().toString() : request.getRequestId());
        context.setTraceId(request.getTraceId());
        context.setAuditRequired(true);
        CapabilityInvokeResult invokeResult = capabilityInvoker.invoke(provider.getPluginId(), provider.getKey(), providerPayload(channel, request), context);
        NotificationDelivery delivery = baseDelivery(channel, provider);
        if (invokeResult.isSuccess()) {
            delivery.setStatus("success");
        } else {
            delivery.setStatus("error");
            delivery.setErrorMessage(invokeResult.getErrorMessage());
        }
        return delivery;
    }

    private Map<String, Object> providerPayload(String channel, NotificationRequest request) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("sourcePluginId", request.getSourcePluginId());
        map.put("sourcePluginName", request.getSourcePluginName());
        map.put("sourceCapabilityKey", request.getSourceCapabilityKey());
        map.put("eventType", request.getEventType());
        map.put("notificationType", request.getNotificationType());
        map.put("channel", channel);
        map.put("title", request.getTitle());
        map.put("content", request.getContent());
        map.put("level", request.getLevel());
        map.put("requestId", request.getRequestId());
        map.put("traceId", request.getTraceId());
        map.put("payload", request.getPayload());
        return map;
    }

    private NotificationDelivery failedDelivery(String channel, PluginCapability provider, String errorMessage) {
        NotificationDelivery delivery = baseDelivery(channel, provider);
        delivery.setStatus("error");
        delivery.setErrorMessage(errorMessage);
        return delivery;
    }

    private NotificationDelivery baseDelivery(String channel, PluginCapability provider) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(UUID.randomUUID().toString());
        delivery.setChannel(channel);
        if (provider != null) {
            delivery.setProviderPluginId(provider.getPluginId());
            delivery.setCapabilityKey(provider.getKey());
        }
        delivery.setCreatedAt(RuntimeDates.nowString());
        return delivery;
    }
}
