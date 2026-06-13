package com.zrlog.plugincore.server.web.controller;

import com.google.gson.Gson;
import com.hibegin.common.dao.dto.PageData;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.notification.NotificationDelivery;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomation;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomationRun;
import com.zrlog.plugincore.server.runtime.scheduler.RuntimeSystemAutomations;
import com.zrlog.plugincore.server.vo.PluginVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RuntimeApiResponses {

    private static final Gson GSON = new Gson();

    private RuntimeApiResponses() {
    }

    static Map<String, Object> success() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("code", 0);
        map.put("message", "成功");
        return map;
    }

    static Map<String, Object> error(String message) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("code", 1);
        map.put("message", message == null ? "失败" : message);
        return map;
    }

    static void putPage(Map<String, Object> map, PageData<?> page) {
        map.put("page", page.getPage());
        map.put("size", page.getSize());
        map.put("totalElements", page.getTotalElements());
    }

    static Map<String, Plugin> pluginsById() {
        return pluginsById(PluginCoreDAO.getInstance().loadSnapshot());
    }

    static Map<String, Plugin> pluginsById(PluginCore pluginCore) {
        Map<String, Plugin> pluginsById = new HashMap<String, Plugin>();
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().getPluginVOs(pluginCore)) {
            if (pluginVO == null || pluginVO.getPlugin() == null || isBlank(pluginVO.getPlugin().getId())) {
                continue;
            }
            pluginsById.put(pluginVO.getPlugin().getId(), pluginVO.getPlugin());
        }
        return pluginsById;
    }

    static Map<String, PluginCapability> capabilitiesByKey(List<PluginCapability> capabilities) {
        Map<String, PluginCapability> capabilitiesByKey = new HashMap<String, PluginCapability>();
        for (PluginCapability capability : capabilities) {
            if (capability == null || isBlank(capability.getPluginId()) || isBlank(capability.getKey())) {
                continue;
            }
            capabilitiesByKey.put(capabilityMapKey(capability.getPluginId(), capability.getKey()), capability);
        }
        return capabilitiesByKey;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> capabilityResponses(List<PluginCapability> capabilities, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginCapability capability : capabilities) {
            Map<String, Object> item = GSON.fromJson(GSON.toJson(capability), Map.class);
            Plugin plugin = pluginsById.get(capability.getPluginId());
            item.put("pluginName", pluginDisplayName(plugin));
            item.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
            items.add(item);
        }
        return items;
    }

    static List<Map<String, Object>> automationResponses(List<PluginAutomation> automations,
                                                          Map<String, Plugin> pluginsById,
                                                          Map<String, PluginCapability> capabilitiesByKey) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginAutomation automation : automations) {
            items.add(automationResponse(automation, pluginsById, capabilitiesByKey));
        }
        return items;
    }

    static Map<String, Object> automationResponse(PluginAutomation automation,
                                                   Map<String, Plugin> pluginsById,
                                                   Map<String, PluginCapability> capabilitiesByKey) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", automation.getId());
        map.put("pluginId", automation.getPluginId());
        Plugin plugin = pluginsById.get(automation.getPluginId());
        PluginCapability capability = capabilitiesByKey.get(capabilityMapKey(automation.getPluginId(), automation.getCapabilityKey()));
        map.put("pluginName", runtimePluginDisplayName(automation.getPluginId(), plugin, capability));
        map.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
        map.put("capabilityKey", automation.getCapabilityKey());
        map.put("name", automation.getName());
        map.put("triggerType", automation.getTriggerType());
        map.put("cron", automation.getCron());
        map.put("timezone", automation.getTimezone());
        map.put("enabled", automation.getEnabled());
        map.put("system", automation.getSystem());
        map.put("deletable", automation.getDeletable());
        map.put("nextRunAt", automation.getNextRunAt());
        map.put("lastRunAt", automation.getLastRunAt());
        map.put("leaseOwner", automation.getLeaseOwner());
        map.put("leaseUntil", automation.getLeaseUntil());
        map.put("payload", automation.getPayload());
        map.put("targetLabel", automationTargetLabel(automation.getId(), automation.getPluginId(), automation.getCapabilityKey(), automation.getName(), plugin, capability));
        return map;
    }

    static List<Map<String, Object>> automationRunResponses(List<PluginAutomationRun> runs,
                                                             Map<String, Plugin> pluginsById,
                                                             Map<String, PluginCapability> capabilitiesByKey) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginAutomationRun run : runs) {
            items.add(automationRunResponse(run, pluginsById, capabilitiesByKey));
        }
        return items;
    }

    static Map<String, Object> automationRunResponse(PluginAutomationRun run,
                                                      Map<String, Plugin> pluginsById,
                                                      Map<String, PluginCapability> capabilitiesByKey) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", run.getId());
        map.put("automationId", run.getAutomationId());
        map.put("pluginId", run.getPluginId());
        Plugin plugin = pluginsById.get(run.getPluginId());
        PluginCapability capability = capabilitiesByKey.get(capabilityMapKey(run.getPluginId(), run.getCapabilityKey()));
        map.put("pluginName", runtimePluginDisplayName(run.getPluginId(), plugin, capability));
        map.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
        map.put("capabilityKey", run.getCapabilityKey());
        map.put("status", run.getStatus());
        map.put("startedAt", run.getStartedAt());
        map.put("finishedAt", run.getFinishedAt());
        map.put("durationMs", run.getDurationMs());
        map.put("errorMessage", run.getErrorMessage());
        map.put("targetLabel", automationTargetLabel(run.getAutomationId(), run.getPluginId(), run.getCapabilityKey(), null, plugin, capability));
        return map;
    }

    static List<Map<String, Object>> invocationLogResponses(List<CapabilityInvocationLog> logs, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (CapabilityInvocationLog log : logs) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("id", log.getId());
            map.put("pluginId", log.getPluginId());
            Plugin plugin = pluginsById.get(log.getPluginId());
            map.put("pluginName", runtimePluginDisplayName(log.getPluginId(), plugin));
            map.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
            map.put("capabilityKey", log.getCapabilityKey());
            map.put("source", log.getSource());
            map.put("riskLevel", log.getRiskLevel());
            map.put("auditRequired", log.getAuditRequired());
            map.put("requestId", log.getRequestId());
            map.put("traceId", log.getTraceId());
            map.put("status", log.getStatus());
            map.put("startedAt", log.getStartedAt());
            map.put("finishedAt", log.getFinishedAt());
            map.put("durationMs", log.getDurationMs());
            map.put("errorMessage", log.getErrorMessage());
            items.add(map);
        }
        return items;
    }

    static List<Map<String, Object>> notificationDeliveryResponses(List<NotificationDelivery> deliveries, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (NotificationDelivery delivery : deliveries) {
            items.add(notificationDeliveryResponse(delivery, pluginsById));
        }
        return items;
    }

    static Map<String, Object> notificationDeliveryResponse(NotificationDelivery delivery, Map<String, Plugin> pluginsById) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", delivery.getId());
        map.put("channel", delivery.getChannel());
        map.put("providerPluginId", delivery.getProviderPluginId());
        Plugin plugin = pluginsById.get(delivery.getProviderPluginId());
        map.put("providerPluginName", pluginDisplayName(plugin));
        map.put("providerPluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
        map.put("capabilityKey", delivery.getCapabilityKey());
        map.put("status", delivery.getStatus());
        map.put("errorMessage", delivery.getErrorMessage());
        map.put("createdAt", delivery.getCreatedAt());
        return map;
    }

    static String automationTargetLabel(String id,
                                        String pluginId,
                                        String capabilityKey,
                                        String fallbackName,
                                        Plugin plugin,
                                        PluginCapability capability) {
        String systemTargetLabel = systemAutomationTargetLabel(id, pluginId, capabilityKey);
        if (systemTargetLabel != null) {
            return systemTargetLabel;
        }
        return runtimePluginDisplayName(pluginId, plugin, capability) + " / " + capabilityDisplayLabel(capabilityKey, fallbackName, capability);
    }

    static Map<String, NotificationDelivery> latestNotificationDeliveryByProvider(List<NotificationDelivery> deliveries) {
        Map<String, NotificationDelivery> latest = new HashMap<String, NotificationDelivery>();
        if (deliveries == null) {
            return latest;
        }
        for (NotificationDelivery delivery : deliveries) {
            if (delivery == null) {
                continue;
            }
            String key = notificationProviderKey(delivery.getChannel(), delivery.getProviderPluginId(), delivery.getCapabilityKey());
            NotificationDelivery existing = latest.get(key);
            if (existing == null || deliveryTime(delivery) >= deliveryTime(existing)) {
                latest.put(key, delivery);
            }
        }
        return latest;
    }

    static String notificationProviderKey(String channel, String pluginId, String capabilityKey) {
        return Objects.toString(channel, "") + "\n" + Objects.toString(pluginId, "") + "\n" + Objects.toString(capabilityKey, "");
    }

    static String pluginPreviewImageBase64(Plugin plugin) {
        return plugin == null || isBlank(plugin.getPreviewImageBase64()) ? "" : plugin.getPreviewImageBase64();
    }

    static String pluginDisplayName(Plugin plugin) {
        return plugin == null || isBlank(plugin.getName()) ? "未命名插件" : plugin.getName();
    }

    private static String capabilityDisplayLabel(String capabilityKey, String fallbackName, PluginCapability capability) {
        if (capability != null && !isBlank(capability.getLabel())) {
            return capability.getLabel();
        }
        if (!isBlank(fallbackName)) {
            return fallbackName;
        }
        return isBlank(capabilityKey) ? "未命名任务" : capabilityKey;
    }

    private static String capabilityMapKey(String pluginId, String capabilityKey) {
        return Objects.toString(pluginId, "") + "\n" + Objects.toString(capabilityKey, "");
    }

    private static String systemAutomationTargetLabel(String id, String pluginId, String capabilityKey) {
        if (RuntimeSystemAutomations.isRuntimeMaintenanceIdentity(id, pluginId, capabilityKey)) {
            return RuntimeSystemAutomations.runtimeMaintenanceTargetLabel();
        }
        return null;
    }

    private static String runtimePluginDisplayName(String pluginId, Plugin plugin) {
        return runtimePluginDisplayName(pluginId, plugin, null);
    }

    private static String runtimePluginDisplayName(String pluginId, Plugin plugin, PluginCapability capability) {
        if (RuntimeSystemAutomations.isSystemPluginId(pluginId)) {
            return RuntimeSystemAutomations.systemPluginName();
        }
        if (plugin != null && !isBlank(plugin.getName())) {
            return plugin.getName();
        }
        if (capability != null && !isBlank(capability.getPluginName())) {
            return capability.getPluginName();
        }
        return pluginDisplayName(plugin);
    }

    private static long deliveryTime(NotificationDelivery delivery) {
        return delivery.getCreatedAt() == null ? 0 : delivery.getCreatedAt();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
