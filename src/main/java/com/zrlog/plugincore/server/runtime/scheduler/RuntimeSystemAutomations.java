package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.CronParseException;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuntimeSystemAutomations {

    static final String RUNTIME_MAINTENANCE_ID = "system:plugin-runtime-maintenance";
    static final String SYSTEM_PLUGIN_ID = "__system__";
    static final String RUNTIME_MAINTENANCE_KEY = "plugin.runtime.maintenance";

    private static final String DEFAULT_RUNTIME_MAINTENANCE_CRON = "*/5 * * * *";
    private static final String RUNTIME_MAINTENANCE_NAME = "运行态维护";

    private RuntimeSystemAutomations() {
    }

    static boolean isRuntimeMaintenance(PluginAutomation automation) {
        return automation != null
                && isRuntimeMaintenanceIdentity(automation.getId(), automation.getPluginId(), automation.getCapabilityKey());
    }

    public static boolean isRuntimeMaintenanceIdentity(String id, String pluginId, String capabilityKey) {
        return Objects.equals(RUNTIME_MAINTENANCE_ID, id)
                || (Objects.equals(SYSTEM_PLUGIN_ID, pluginId)
                && Objects.equals(RUNTIME_MAINTENANCE_KEY, capabilityKey));
    }

    public static String runtimeMaintenanceTargetLabel() {
        return "系统任务 / " + RUNTIME_MAINTENANCE_NAME;
    }

    public static boolean isSystemPluginId(String pluginId) {
        return Objects.equals(SYSTEM_PLUGIN_ID, pluginId);
    }

    public static String systemPluginName() {
        return "系统";
    }

    static boolean ensureRuntimeMaintenance(List<PluginAutomation> automations,
                                            PluginRuntimeSetting setting,
                                            BasicCronParser cronParser,
                                            ZonedDateTime now) {
        PluginAutomation existing = null;
        for (PluginAutomation automation : automations) {
            if (isRuntimeMaintenance(automation)) {
                existing = automation;
                break;
            }
        }
        if (existing == null) {
            PluginAutomation automation = new PluginAutomation();
            automation.setId(RUNTIME_MAINTENANCE_ID);
            automation.setName(RUNTIME_MAINTENANCE_NAME);
            automation.setPluginId(SYSTEM_PLUGIN_ID);
            automation.setCapabilityKey(RUNTIME_MAINTENANCE_KEY);
            automation.setCron(DEFAULT_RUNTIME_MAINTENANCE_CRON);
            automation.setEnabled(Boolean.TRUE);
            automation.setPayload(runtimePayload(setting));
            prepareSystemAutomation(automation, cronParser, now);
            automations.add(automation);
            return true;
        }
        return normalizeRuntimeMaintenance(existing, setting, cronParser, now);
    }

    static PluginAutomation prepareRuntimeMaintenanceSave(PluginAutomation input,
                                                          BasicCronParser cronParser,
                                                          ZonedDateTime now) {
        if (input == null) {
            throw new CronParseException("Automation is empty");
        }
        PluginAutomation automation = new PluginAutomation();
        automation.setId(RUNTIME_MAINTENANCE_ID);
        automation.setName(RUNTIME_MAINTENANCE_NAME);
        automation.setPluginId(SYSTEM_PLUGIN_ID);
        automation.setCapabilityKey(RUNTIME_MAINTENANCE_KEY);
        automation.setCron(input.getCron());
        automation.setEnabled(Boolean.TRUE);
        automation.setPayload(runtimePayload(runtimeSettingFromPayload(input.getPayload())));
        prepareSystemAutomation(automation, cronParser, now);
        return automation;
    }

    static PluginRuntimeSetting runtimeSettingFromPayload(Map<String, Object> payload) {
        PluginRuntimeSetting setting = new PluginRuntimeSetting();
        if (payload == null) {
            return setting;
        }
        setting.setOnDemandEnabled(booleanValue(payload.get("onDemandEnabled"), setting.getOnDemandEnabled()));
        setting.setAutoDownloadMissingPluginFileEnabled(booleanValue(payload.get("autoDownloadMissingPluginFileEnabled"),
                setting.getAutoDownloadMissingPluginFileEnabled()));
        setting.setIdleStopEnabled(booleanValue(payload.get("idleStopEnabled"), setting.getIdleStopEnabled()));
        setting.setIdleTimeoutSeconds(longValue(payload.get("idleTimeoutSeconds"), setting.getIdleTimeoutSeconds(), 10L));
        normalizeLoadStrategy(setting);
        return setting;
    }

    static Map<String, Object> runtimePayload(PluginRuntimeSetting setting) {
        PluginRuntimeSetting runtimeSetting = normalizedRuntimeSetting(setting);
        Map<String, Object> payload = new HashMap<>();
        payload.put("onDemandEnabled", runtimeSetting.getOnDemandEnabled());
        payload.put("autoDownloadMissingPluginFileEnabled", runtimeSetting.getAutoDownloadMissingPluginFileEnabled());
        payload.put("idleStopEnabled", runtimeSetting.getIdleStopEnabled());
        payload.put("idleTimeoutSeconds", runtimeSetting.getIdleTimeoutSeconds());
        return payload;
    }

    private static boolean normalizeRuntimeMaintenance(PluginAutomation automation,
                                                       PluginRuntimeSetting setting,
                                                       BasicCronParser cronParser,
                                                       ZonedDateTime now) {
        boolean changed = false;
        ZoneId zoneId = ZoneId.systemDefault();
        if (!Objects.equals(RUNTIME_MAINTENANCE_ID, automation.getId())) {
            automation.setId(RUNTIME_MAINTENANCE_ID);
            changed = true;
        }
        if (isBlank(automation.getName())) {
            automation.setName(RUNTIME_MAINTENANCE_NAME);
            changed = true;
        }
        if (!Objects.equals(SYSTEM_PLUGIN_ID, automation.getPluginId())) {
            automation.setPluginId(SYSTEM_PLUGIN_ID);
            changed = true;
        }
        if (!Objects.equals(RUNTIME_MAINTENANCE_KEY, automation.getCapabilityKey())) {
            automation.setCapabilityKey(RUNTIME_MAINTENANCE_KEY);
            changed = true;
        }
        if (isBlank(automation.getCron())) {
            automation.setCron(DEFAULT_RUNTIME_MAINTENANCE_CRON);
            changed = true;
        }
        Map<String, Object> normalizedPayload = automation.getPayload() == null || automation.getPayload().isEmpty()
                ? runtimePayload(setting)
                : runtimePayload(runtimeSettingFromPayload(automation.getPayload()));
        if (!Objects.equals(normalizedPayload, automation.getPayload())) {
            automation.setPayload(normalizedPayload);
            changed = true;
        }
        if (!Objects.equals("cron", automation.getTriggerType())) {
            automation.setTriggerType("cron");
            changed = true;
        }
        if (!Objects.equals(zoneId.getId(), automation.getTimezone())) {
            automation.setTimezone(zoneId.getId());
            changed = true;
        }
        if (!Boolean.TRUE.equals(automation.getEnabled())) {
            automation.setEnabled(Boolean.TRUE);
            changed = true;
        }
        if (!Boolean.TRUE.equals(automation.getSystem())) {
            automation.setSystem(Boolean.TRUE);
            changed = true;
        }
        if (!Boolean.FALSE.equals(automation.getDeletable())) {
            automation.setDeletable(Boolean.FALSE);
            changed = true;
        }
        if (automation.getNextRunAt() == null) {
            automation.setNextRunAt(SchedulerTimes.nextRunAtMillis(cronParser, automation.getCron(), zoneId, now));
            changed = true;
        }
        return changed;
    }

    private static void prepareSystemAutomation(PluginAutomation automation,
                                                BasicCronParser cronParser,
                                                ZonedDateTime now) {
        if (isBlank(automation.getCron())) {
            throw new CronParseException("Cron expression is empty");
        }
        ZoneId zoneId = ZoneId.systemDefault();
        automation.setTriggerType("cron");
        automation.setTimezone(zoneId.getId());
        automation.setEnabled(Boolean.TRUE);
        automation.setSystem(Boolean.TRUE);
        automation.setDeletable(Boolean.FALSE);
        automation.setNextRunAt(SchedulerTimes.nextRunAtMillis(cronParser, automation.getCron(), zoneId, now));
    }

    private static Boolean booleanValue(Object value, Boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static Long longValue(Object value, Long fallback, long min) {
        if (value == null) {
            return fallback;
        }
        long number;
        if (value instanceof Number) {
            number = ((Number) value).longValue();
        } else {
            number = Long.parseLong(value.toString());
        }
        return Math.max(min, number);
    }

    private static void normalizeLoadStrategy(PluginRuntimeSetting setting) {
        if (!setting.getOnDemandEnabled()) {
            setting.setIdleStopEnabled(Boolean.FALSE);
        }
    }

    private static PluginRuntimeSetting normalizedRuntimeSetting(PluginRuntimeSetting setting) {
        PluginRuntimeSetting normalized = new PluginRuntimeSetting();
        if (setting != null) {
            normalized.setOnDemandEnabled(setting.getOnDemandEnabled());
            normalized.setAutoDownloadMissingPluginFileEnabled(setting.getAutoDownloadMissingPluginFileEnabled());
            normalized.setIdleStopEnabled(setting.getIdleStopEnabled());
            normalized.setIdleTimeoutSeconds(setting.getIdleTimeoutSeconds());
            normalized.setIdleScanIntervalSeconds(setting.getIdleScanIntervalSeconds());
        }
        normalizeLoadStrategy(normalized);
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
