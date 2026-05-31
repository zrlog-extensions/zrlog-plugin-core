package com.zrlog.plugincore.server.controller;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.dao.WebSiteDAO;
import com.zrlog.plugincore.server.plugin.PluginBootstrap;
import com.zrlog.plugincore.server.plugin.PluginSessions;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.RuntimeCapabilityInvokerFactory;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogStore;
import com.zrlog.plugincore.server.runtime.notification.*;
import com.zrlog.plugincore.server.runtime.scheduler.*;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderResolver;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderSetting;
import com.zrlog.plugincore.server.runtime.service.ServiceSetting;
import com.zrlog.plugincore.server.runtime.state.*;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.util.RuntimePage;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class RuntimeApiController extends Controller {

    private static final String COMMENT_PLUGIN_NAME_KEY = "comment_plugin_name";
    private static final String DEFAULT_COMMENT_PLUGIN = "comment";

    private final Gson gson = new Gson();

    @ResponseBody
    public Map<String, Object> schedulerSettings() {
        PluginCoreDAO pluginCoreDAO = PluginCoreDAO.getInstance();
        SchedulerSetting setting = pluginCoreDAO.loadSnapshot().getSetting().getScheduler();
        if (getRequest().getMethod() == HttpMethod.POST) {
            String externalTickEnabled = getRequest().getParaToStr("externalTickEnabled");
            setting = pluginCoreDAO.update(pluginCore -> {
                SchedulerSetting scheduler = pluginCore.getSetting().getScheduler();
                scheduler.setExternalHost(normalizeNullable(getRequest().getParaToStr("externalHost")));
                SchedulerProviderSetting provider = scheduler.ensureDefaultProvider();
                if (!isBlank(externalTickEnabled)) {
                    provider.setEnabled(getRequest().getParaToBool("externalTickEnabled"));
                }
            }).getSetting().getScheduler();
        }
        String fallbackHomeUrl = schedulerFallbackHomeUrl(request);
        Map<String, Object> map = success();
        map.put("enabled", setting.getEnabled());
        map.put("externalHost", setting.getExternalHost());
        map.put("effectiveExternalHost", SchedulerExternalEndpoint.effectiveHost(setting.getExternalHost(), fallbackHomeUrl));
        map.put("externalTickPath", SchedulerExternalEndpoint.EXTERNAL_TICK_PATH);
        map.put("externalTickUrl", SchedulerExternalEndpoint.tickUrl(setting.getExternalHost(), fallbackHomeUrl));
        map.put("providers", setting.getProviders());
        map.put("systemTimezone", ZoneId.systemDefault().getId());
        return map;
    }

    @ResponseBody
    public Map<String, Object> automations() {
        if (getRequest().getMethod() == HttpMethod.POST) {
            return saveAutomation();
        }
        Map<String, Object> map = success();
        map.put("items", automationResponses(automationService().listWithSystemAutomations(), pluginsById(), capabilitiesByKey()));
        map.put("systemTimezone", ZoneId.systemDefault().getId());
        return map;
    }

    @ResponseBody
    public Map<String, Object> automationUpdate() {
        return saveAutomation();
    }

    @ResponseBody
    public Map<String, Object> automationDelete() {
        boolean removed = automationService().delete(getRequest().getParaToStr("id"));
        Map<String, Object> map = success();
        map.put("removed", removed);
        return map;
    }

    @ResponseBody
    public Map<String, Object> automationRunNow() {
        try {
            PluginAutomationRun run = schedulerTickService().runNow(getRequest().getParaToStr("id"), ZonedDateTime.now());
            Map<String, Object> map = success();
            map.put("item", automationRunResponse(run, pluginsById(), capabilitiesByKey()));
            return map;
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> schedulerTick() {
        try {
            SchedulerTickResult result = schedulerTickService().tick(ZonedDateTime.now());
            Map<String, Object> map = success();
            map.put("result", result);
            return map;
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> automationRuns() {
        Map<String, Object> map = success();
        RuntimePage<PluginAutomationRun> page = newestPage(automationRunStore().list(), 8);
        map.put("items", automationRunResponses(page.getItems(), pluginsById(), capabilitiesByKey()));
        putPage(map, page);
        return map;
    }

    @ResponseBody
    public Map<String, Object> runtimeStates() {
        Map<String, Object> map = success();
        map.put("items", runtimeStatesForCurrentMode());
        return map;
    }

    @ResponseBody
    public Map<String, Object> runtimeStart() {
        String pluginId = getRequest().getParaToStr("pluginId");
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return error("插件不存在");
        }
        boolean started = runtimeStateService().ensureStarted(pluginVO.getPlugin().getId());
        if (!started) {
            return error("插件启动失败");
        }
        Map<String, Object> map = success();
        map.put("started", true);
        return map;
    }

    @ResponseBody
    public Map<String, Object> runtimeStop() {
        String pluginId = getRequest().getParaToStr("pluginId");
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return error("插件不存在");
        }
        if (activeInvocationCount(pluginId) > 0) {
            return error("插件正在执行任务");
        }
        String pluginName = pluginDisplayName(pluginVO.getPlugin());
        try {
            runtimeStateService().markStopping(pluginId, pluginName);
            PluginBootstrap.stopPlugin(pluginVO.getPlugin().getShortName());
            runtimeStateService().markStopped(pluginId, pluginName);
            return success();
        } catch (RuntimeException e) {
            runtimeStateService().markFailed(pluginId, pluginName, e.getMessage());
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> runtimeSettings() {
        PluginCoreDAO pluginCoreDAO = PluginCoreDAO.getInstance();
        PluginRuntimeSetting setting = pluginCoreDAO.loadSnapshot().getSetting().getRuntime();
        if (getRequest().getMethod() == HttpMethod.POST) {
            try {
                Boolean onDemandEnabled = runtimeBooleanParam("onDemandEnabled", setting.getOnDemandEnabled());
                Boolean idleStopEnabled = runtimeBooleanParam("idleStopEnabled", setting.getIdleStopEnabled());
                Long idleTimeoutSeconds = runtimeLongParam("idleTimeoutSeconds", setting.getIdleTimeoutSeconds(), 10L);
                Long idleScanIntervalSeconds = runtimeLongParam("idleScanIntervalSeconds", setting.getIdleScanIntervalSeconds(), 5L);
                setting = pluginCoreDAO.update(pluginCore -> {
                    PluginRuntimeSetting runtime = pluginCore.getSetting().getRuntime();
                    runtime.setOnDemandEnabled(onDemandEnabled);
                    runtime.setIdleStopEnabled(idleStopEnabled);
                    runtime.setIdleTimeoutSeconds(idleTimeoutSeconds);
                    runtime.setIdleScanIntervalSeconds(idleScanIntervalSeconds);
                }).getSetting().getRuntime();
                PluginBootstrap.loadPluginsAsync();
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }
        }
        return runtimeSettingsResponse(setting);
    }

    @ResponseBody
    public Map<String, Object> invocationLogs() {
        Map<String, Object> map = success();
        RuntimePage<CapabilityInvocationLog> page = newestPage(invocationLogStore().list(), 10);
        map.put("items", invocationLogResponses(page.getItems(), pluginsById()));
        putPage(map, page);
        return map;
    }

    @ResponseBody
    public Map<String, Object> capabilities() {
        CapabilityStore store = capabilityStore();
        String pluginId = getRequest().getParaToStr("pluginId");
        String type = getRequest().getParaToStr("type");
        String exposure = getRequest().getParaToStr("exposure");
        List<PluginCapability> items = store.listAll();
        if (!isBlank(pluginId)) {
            items = items.stream().filter(item -> Objects.equals(pluginId, item.getPluginId())).collect(Collectors.toList());
        }
        if (!isBlank(type)) {
            items = items.stream().filter(item -> Objects.equals(type, item.getType())).collect(Collectors.toList());
        }
        if (!isBlank(exposure)) {
            items = items.stream()
                    .filter(item -> item.getExposure() != null && item.getExposure().contains(exposure))
                    .collect(Collectors.toList());
        }
        Map<String, Object> map = success();
        map.put("items", capabilityResponses(items, pluginsById()));
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capabilityResponses(List<PluginCapability> capabilities, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginCapability capability : capabilities) {
            Map<String, Object> item = gson.fromJson(gson.toJson(capability), Map.class);
            Plugin plugin = pluginsById.get(capability.getPluginId());
            item.put("pluginName", pluginDisplayName(plugin));
            item.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
            items.add(item);
        }
        return items;
    }

    private Map<String, Plugin> pluginsById() {
        Map<String, Plugin> pluginsById = new HashMap<String, Plugin>();
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().getPluginVOs()) {
            if (pluginVO == null || pluginVO.getPlugin() == null || isBlank(pluginVO.getPlugin().getId())) {
                continue;
            }
            pluginsById.put(pluginVO.getPlugin().getId(), pluginVO.getPlugin());
        }
        return pluginsById;
    }

    private Map<String, PluginCapability> capabilitiesByKey() {
        Map<String, PluginCapability> capabilitiesByKey = new HashMap<String, PluginCapability>();
        for (PluginCapability capability : capabilityStore().listAll()) {
            if (capability == null || isBlank(capability.getPluginId()) || isBlank(capability.getKey())) {
                continue;
            }
            capabilitiesByKey.put(capabilityMapKey(capability.getPluginId(), capability.getKey()), capability);
        }
        return capabilitiesByKey;
    }

    private String pluginPreviewImageBase64(Plugin plugin) {
        return plugin == null || isBlank(plugin.getPreviewImageBase64()) ? "" : plugin.getPreviewImageBase64();
    }

    private static String pluginDisplayName(Plugin plugin) {
        return plugin == null || isBlank(plugin.getName()) ? "未命名插件" : plugin.getName();
    }

    @ResponseBody
    public Map<String, Object> notificationChannels() {
        List<PluginCapability> providers = capabilityStore().listByType("notification_channel").stream()
                .filter(item -> item.getExposure() != null && item.getExposure().contains("notification"))
                .filter(item -> !isBlank(item.getChannel()))
                .collect(Collectors.toList());
        NotificationSetting setting = PluginCoreDAO.getInstance().loadSnapshot().getSetting().getNotification();
        NotificationProviderResolver resolver = new NotificationProviderResolver();
        Set<String> channels = new HashSet<String>();
        for (PluginCapability provider : providers) {
            channels.add(provider.getChannel());
        }
        Map<String, Plugin> pluginsById = pluginsById();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (String channel : channels) {
            PluginCapability selected = resolver.resolve(channel, providers, setting).orElse(null);
            boolean reviewRequired = resolver.reviewRequired(channel, providers, setting);
            for (PluginCapability provider : providers) {
                if (!Objects.equals(channel, provider.getChannel())) {
                    continue;
                }
                Map<String, Object> item = new HashMap<String, Object>();
                item.put("channel", channel);
                item.put("providerPluginId", provider.getPluginId());
                Plugin plugin = pluginsById.get(provider.getPluginId());
                item.put("providerPluginName", pluginDisplayName(plugin));
                item.put("providerPluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
                item.put("capabilityKey", provider.getKey());
                item.put("capabilityLabel", provider.getLabel());
                item.put("providerStatus", "available");
                item.put("selected", selected != null
                        && Objects.equals(selected.getPluginId(), provider.getPluginId())
                        && Objects.equals(selected.getKey(), provider.getKey()));
                item.put("confirmed", isConfiguredProvider(setting, channel, provider));
                item.put("reviewRequired", reviewRequired);
                items.add(item);
            }
        }
        Map<String, Object> map = success();
        map.put("items", items);
        return map;
    }

    @ResponseBody
    public Map<String, Object> notificationProviderUpdate() {
        String channel = getRequest().getParaToStr("channel");
        String pluginId = getRequest().getParaToStr("pluginId");
        String capabilityKey = getRequest().getParaToStr("capabilityKey");
        PluginCapability provider = capabilityStore().find(pluginId, capabilityKey).orElse(null);
        if (provider == null || !Objects.equals("notification_channel", provider.getType())
                || provider.getExposure() == null || !provider.getExposure().contains("notification")
                || !Objects.equals(channel, provider.getChannel())) {
            return error("通知通道能力不存在");
        }
        NotificationProviderSetting providerSetting = new NotificationProviderSetting();
        providerSetting.setChannel(channel);
        providerSetting.setPluginId(pluginId);
        providerSetting.setCapabilityKey(capabilityKey);
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getNotification().getDefaultProviders().put(channel, providerSetting));
        return success();
    }

    @ResponseBody
    public Map<String, Object> notificationProviderAuto() {
        String channel = getRequest().getParaToStr("channel");
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getNotification().getDefaultProviders().remove(channel));
        return success();
    }

    @ResponseBody
    public Map<String, Object> notificationDeliveries() {
        Map<String, Object> map = success();
        RuntimePage<NotificationDelivery> page = newestPage(notificationDeliveryStore().list(), 8);
        map.put("items", notificationDeliveryResponses(page.getItems(), pluginsById()));
        putPage(map, page);
        return map;
    }

    @ResponseBody
    public Map<String, Object> serviceProviders() {
        ServiceProviderResolver resolver = new ServiceProviderResolver();
        List<PluginCapability> providers = capabilityStore().listByType("service").stream()
                .filter(item -> item.getExposure() != null && item.getExposure().contains("internal"))
                .filter(item -> !isBlank(resolver.serviceNameFor(item)))
                .collect(Collectors.toList());
        ServiceSetting setting = PluginCoreDAO.getInstance().loadSnapshot().getSetting().getService();
        Set<String> serviceNames = new HashSet<String>();
        for (PluginCapability provider : providers) {
            serviceNames.add(resolver.serviceNameFor(provider));
        }
        List<String> sortedServiceNames = new ArrayList<String>(serviceNames);
        Collections.sort(sortedServiceNames);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Map<String, Plugin> pluginsById = pluginsById();
        for (String serviceName : sortedServiceNames) {
            PluginCapability selected = resolver.resolve(serviceName, providers, setting).orElse(null);
            boolean reviewRequired = resolver.reviewRequired(serviceName, providers, setting);
            for (PluginCapability provider : resolver.providersFor(serviceName, providers)) {
                Map<String, Object> item = new HashMap<String, Object>();
                item.put("serviceName", serviceName);
                item.put("serviceLabel", serviceLabel(serviceName));
                item.put("providerPluginId", provider.getPluginId());
                Plugin plugin = pluginsById.get(provider.getPluginId());
                item.put("providerPluginName", pluginDisplayName(plugin));
                item.put("providerPluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
                item.put("capabilityKey", provider.getKey());
                item.put("capabilityLabel", provider.getLabel());
                item.put("selected", selected != null
                        && Objects.equals(selected.getPluginId(), provider.getPluginId())
                        && Objects.equals(selected.getKey(), provider.getKey()));
                item.put("confirmed", isConfiguredServiceProvider(setting, serviceName, provider));
                item.put("reviewRequired", reviewRequired);
                items.add(item);
            }
        }
        Map<String, Object> map = success();
        map.put("items", items);
        return map;
    }

    @ResponseBody
    public Map<String, Object> serviceProviderUpdate() {
        String serviceName = getRequest().getParaToStr("serviceName");
        String pluginId = getRequest().getParaToStr("pluginId");
        String capabilityKey = getRequest().getParaToStr("capabilityKey");
        List<PluginCapability> serviceCapabilities = capabilityStore().listByType("service");
        PluginCapability provider = new ServiceProviderResolver().providersFor(serviceName, serviceCapabilities).stream()
                .filter(item -> Objects.equals(pluginId, item.getPluginId()))
                .filter(item -> Objects.equals(capabilityKey, item.getKey()))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            return error("服务能力不存在");
        }
        ServiceProviderSetting providerSetting = new ServiceProviderSetting();
        providerSetting.setServiceName(serviceName);
        providerSetting.setPluginId(pluginId);
        providerSetting.setCapabilityKey(capabilityKey);
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getService().getDefaultProviders().put(serviceName, providerSetting));
        return success();
    }

    @ResponseBody
    public Map<String, Object> serviceProviderAuto() {
        String serviceName = getRequest().getParaToStr("serviceName");
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getService().getDefaultProviders().remove(serviceName));
        return success();
    }

    @ResponseBody
    public Map<String, Object> commentProviders() {
        try {
            List<Plugin> providers = commentProviderPlugins();
            String configured = commentPluginName();
            String selectedShortName = selectedCommentShortName(providers, configured);
            boolean reviewRequired = providers.size() > 1 && (isBlank(configured) || findPluginByShortName(providers, configured) == null);
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (Plugin provider : providers) {
                Map<String, Object> item = new HashMap<String, Object>();
                item.put("shortName", provider.getShortName());
                item.put("pluginId", provider.getId());
                item.put("pluginName", pluginDisplayName(provider));
                item.put("pluginPreviewImageBase64", pluginPreviewImageBase64(provider));
                item.put("description", provider.getDesc());
                item.put("selected", Objects.equals(selectedShortName, provider.getShortName()));
                item.put("confirmed", !isBlank(configured) && Objects.equals(configured, provider.getShortName()));
                item.put("reviewRequired", reviewRequired);
                items.add(item);
            }
            Map<String, Object> map = success();
            map.put("items", items);
            map.put("selectedShortName", selectedShortName);
            return map;
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> commentProviderUpdate() {
        String shortName = getRequest().getParaToStr("shortName");
        if (findPluginByShortName(commentProviderPlugins(), shortName) == null) {
            return error("评论插件不存在");
        }
        try {
            new WebSiteDAO().saveOrUpdate(COMMENT_PLUGIN_NAME_KEY, shortName);
            return success();
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> commentProviderDefault() {
        List<Plugin> providers = commentProviderPlugins();
        String shortName = findPluginByShortName(providers, DEFAULT_COMMENT_PLUGIN) == null && !providers.isEmpty()
                ? providers.get(0).getShortName()
                : DEFAULT_COMMENT_PLUGIN;
        try {
            new WebSiteDAO().saveOrUpdate(COMMENT_PLUGIN_NAME_KEY, shortName);
            return success();
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    private Map<String, Object> saveAutomation() {
        try {
            PluginAutomation automation = readAutomation();
            Map<String, Object> map = success();
            map.put("item", automationResponse(automationService().save(automation, null), pluginsById(), capabilitiesByKey()));
            return map;
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private List<Map<String, Object>> automationResponses(List<PluginAutomation> automations,
                                                          Map<String, Plugin> pluginsById,
                                                          Map<String, PluginCapability> capabilitiesByKey) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginAutomation automation : automations) {
            items.add(automationResponse(automation, pluginsById, capabilitiesByKey));
        }
        return items;
    }

    private Map<String, Object> automationResponse(PluginAutomation automation,
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

    private List<Map<String, Object>> automationRunResponses(List<PluginAutomationRun> runs,
                                                             Map<String, Plugin> pluginsById,
                                                             Map<String, PluginCapability> capabilitiesByKey) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PluginAutomationRun run : runs) {
            items.add(automationRunResponse(run, pluginsById, capabilitiesByKey));
        }
        return items;
    }

    private Map<String, Object> automationRunResponse(PluginAutomationRun run,
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

    private List<Map<String, Object>> invocationLogResponses(List<CapabilityInvocationLog> logs, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (CapabilityInvocationLog log : logs) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("id", log.getId());
            map.put("pluginId", log.getPluginId());
            Plugin plugin = pluginsById.get(log.getPluginId());
            map.put("pluginName", pluginDisplayName(plugin));
            map.put("pluginPreviewImageBase64", pluginPreviewImageBase64(plugin));
            map.put("capabilityKey", log.getCapabilityKey());
            map.put("source", log.getSource());
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

    private List<Map<String, Object>> notificationDeliveryResponses(List<NotificationDelivery> deliveries, Map<String, Plugin> pluginsById) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (NotificationDelivery delivery : deliveries) {
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
            items.add(map);
        }
        return items;
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

    @SuppressWarnings("unchecked")
    private PluginAutomation readAutomation() {
        PluginAutomation automation = new PluginAutomation();
        automation.setId(getRequest().getParaToStr("id"));
        automation.setName(getRequest().getParaToStr("name"));
        automation.setPluginId(getRequest().getParaToStr("pluginId"));
        automation.setCapabilityKey(getRequest().getParaToStr("capabilityKey"));
        automation.setCron(getRequest().getParaToStr("cron"));
        automation.setEnabled(getRequest().getParaToBool("enabled"));
        String payload = getRequest().getParaToStr("payload");
        if (!isBlank(payload)) {
            automation.setPayload(gson.fromJson(payload, Map.class));
        }
        return automation;
    }

    private boolean isConfiguredProvider(NotificationSetting setting, String channel, PluginCapability provider) {
        if (setting == null || setting.getDefaultProviders() == null) {
            return false;
        }
        NotificationProviderSetting configured = setting.getDefaultProviders().get(channel);
        return configured != null
                && Objects.equals(configured.getPluginId(), provider.getPluginId())
                && Objects.equals(configured.getCapabilityKey(), provider.getKey());
    }

    private boolean isConfiguredServiceProvider(ServiceSetting setting, String serviceName, PluginCapability provider) {
        if (setting == null || setting.getDefaultProviders() == null) {
            return false;
        }
        ServiceProviderSetting configured = setting.getDefaultProviders().get(serviceName);
        return configured != null
                && Objects.equals(configured.getPluginId(), provider.getPluginId())
                && Objects.equals(configured.getCapabilityKey(), provider.getKey());
    }

    private String serviceLabel(String serviceName) {
        if (Objects.equals("uploadService", serviceName)) {
            return "上传服务";
        }
        if (Objects.equals("uploadToPrivateService", serviceName)) {
            return "私有上传服务";
        }
        if (Objects.equals("emailService", serviceName)) {
            return "邮件服务";
        }
        return serviceName;
    }

    private List<Plugin> commentProviderPlugins() {
        if (Boolean.TRUE.equals(Application.nativeAgent)) {
            return Collections.emptyList();
        }
        List<Plugin> providers = new ArrayList<>();
        for (Plugin plugin : PluginSessions.allPlugins()) {
            if (isCommentProviderPlugin(plugin)) {
                providers.add(plugin);
            }
        }
        providers.sort((left, right) -> pluginDisplayName(left).compareTo(pluginDisplayName(right)));
        return providers;
    }

    static List<?> runtimeStatesForCurrentMode() {
        return Boolean.TRUE.equals(Application.nativeAgent) ? Collections.emptyList() : PluginRuntimeStates.runtimeInstancesForDisplay();
    }

    private boolean isCommentProviderPlugin(Plugin plugin) {
        if (plugin == null || plugin.getPaths() == null || plugin.getShortName() == null) {
            return false;
        }
        boolean hasWidget = false;
        boolean hasCommentEndpoint = false;
        for (String path : plugin.getPaths()) {
            String normalizedPath = path == null ? "" : path.trim();
            if (Objects.equals("/widget", normalizedPath) || Objects.equals("/widget/", normalizedPath)) {
                hasWidget = true;
            }
            if (normalizedPath.contains("addComment") || normalizedPath.contains("sync")) {
                hasCommentEndpoint = true;
            }
        }
        if (!hasWidget) {
            return false;
        }
        String text = (plugin.getShortName() + " " + plugin.getName() + " " + plugin.getDesc()).toLowerCase();
        return hasCommentEndpoint || text.contains("comment") || text.contains("评论") || text.contains("changyan");
    }

    private String commentPluginName() throws SQLException {
        Object value = new WebSiteDAO().getWebSiteByNameIn(Collections.singletonList(COMMENT_PLUGIN_NAME_KEY)).get(COMMENT_PLUGIN_NAME_KEY);
        return value == null ? "" : value.toString();
    }

    private String selectedCommentShortName(List<Plugin> providers, String configured) {
        if (!isBlank(configured) && findPluginByShortName(providers, configured) != null) {
            return configured;
        }
        Plugin defaultPlugin = findPluginByShortName(providers, DEFAULT_COMMENT_PLUGIN);
        if (defaultPlugin != null) {
            return defaultPlugin.getShortName();
        }
        return providers.isEmpty() ? "" : providers.get(0).getShortName();
    }

    private Plugin findPluginByShortName(List<Plugin> providers, String shortName) {
        if (isBlank(shortName)) {
            return null;
        }
        for (Plugin provider : providers) {
            if (Objects.equals(shortName, provider.getShortName())) {
                return provider;
            }
        }
        return null;
    }

    private Map<String, Object> runtimeSettingsResponse(PluginRuntimeSetting setting) {
        Map<String, Object> map = success();
        map.put("onDemandEnabled", setting.getOnDemandEnabled());
        map.put("idleStopEnabled", setting.getIdleStopEnabled());
        map.put("idleTimeoutSeconds", setting.getIdleTimeoutSeconds());
        map.put("idleScanIntervalSeconds", setting.getIdleScanIntervalSeconds());
        return map;
    }

    private Boolean runtimeBooleanParam(String name, Boolean fallback) {
        String value = getRequest().getParaToStr(name);
        if (isBlank(value)) {
            return fallback;
        }
        return getRequest().getParaToBool(name);
    }

    private Long runtimeLongParam(String name, Long fallback, long min) {
        String value = getRequest().getParaToStr(name);
        Long number = fallback;
        if (!isBlank(value)) {
            try {
                number = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是数字");
            }
        }
        if (number == null || number < min) {
            return min;
        }
        return number;
    }

    private <T> RuntimePage<T> newestPage(List<T> items, int defaultPageSize) {
        return RuntimePage.newestFirst(items, intParam("page", 1), intParam("pageSize", defaultPageSize), defaultPageSize);
    }

    private void putPage(Map<String, Object> map, RuntimePage<?> page) {
        map.put("page", page.getPage());
        map.put("pageSize", page.getPageSize());
        map.put("total", page.getTotal());
    }

    private int intParam(String name, int fallback) {
        String value = getRequest().getParaToStr(name);
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String normalizeNullable(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String schedulerFallbackHomeUrl(HttpRequest request) {
        return request.getScheme() + "://" + request.getHeader("Host") + request.getContextPath();
    }

    private RuntimeAutomationService automationService() {
        return new RuntimeAutomationService(automationStore(), capabilityStore(), new BasicCronParser());
    }

    private SchedulerTickService schedulerTickService() {
        SchedulerRuntime runtime = new SchedulerRuntime(
                automationStore(),
                automationRunStore(),
                capabilityStore(),
                RuntimeCapabilityInvokerFactory.socket(kvStore()),
                new BasicCronParser()
        );
        return new SchedulerTickService(PluginCoreDAO.getInstance().loadSnapshot().getSetting().getScheduler(), runtime);
    }

    private AutomationStore automationStore() {
        return new AutomationStore(kvStore());
    }

    private AutomationRunStore automationRunStore() {
        return new AutomationRunStore(kvStore());
    }

    private CapabilityStore capabilityStore() {
        return new CapabilityStore(kvStore());
    }

    private NotificationDeliveryStore notificationDeliveryStore() {
        return new NotificationDeliveryStore(kvStore());
    }

    private PluginRuntimeStateService runtimeStateService() {
        return new PluginRuntimeStateService(runtimeStateStore(), new DefaultPluginRuntimeStarter());
    }

    private int activeInvocationCount(String pluginId) {
        return runtimeStateStore().find(pluginId)
                .map(state -> state.getActiveInvocationCount() == null ? 0 : state.getActiveInvocationCount())
                .orElse(0);
    }

    private PluginRuntimeStateStore runtimeStateStore() {
        return new PluginRuntimeStateStore(kvStore());
    }

    private InvocationLogStore invocationLogStore() {
        return new InvocationLogStore(kvStore());
    }

    private KvRepository kvStore() {
        return new WebsiteRuntimeKvStore();
    }

    private Map<String, Object> success() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("code", 0);
        map.put("message", "成功");
        return map;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("code", 1);
        map.put("message", message == null ? "失败" : message);
        return map;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
