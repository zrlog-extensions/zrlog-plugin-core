package com.zrlog.plugincore.server.web.controller;

import com.google.gson.Gson;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.capability.RuntimeSources;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomation;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomationRun;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerExternalEndpoint;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerProviderSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerTickResult;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.*;

public class RuntimeSchedulerApiController extends RuntimeBaseApiController {

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
        map.put("externalTickPath", SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH);
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
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        Map<String, Object> map = success();
        List<PluginCapability> capabilities = capabilityStore().listAll();
        map.put("items", automationResponses(
                automationService(pluginCore.getSetting().getRuntime()).listWithSystemAutomations(),
                pluginsById(pluginCore),
                capabilitiesByKey(capabilities)));
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
            PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
            PluginAutomationRun run = schedulerTickService(pluginCore).runNow(getRequest().getParaToStr("id"), ZonedDateTime.now());
            Map<String, Object> map = success();
            List<PluginCapability> capabilities = capabilityStore().listAll();
            map.put("item", automationRunResponse(run, pluginsById(pluginCore), capabilitiesByKey(capabilities)));
            return map;
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> schedulerTick() {
        try {
            SchedulerTickResult result = schedulerTickService(PluginCoreDAO.getInstance().loadSnapshot())
                    .tick(ZonedDateTime.now(), RuntimeSources.TICK);
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
        PageData<PluginAutomationRun> page = newestPage(automationRunStore().list(), 8);
        List<PluginCapability> capabilities = capabilityStore().listAll();
        map.put("rows", automationRunResponses(page.getRows(), pluginsById(), capabilitiesByKey(capabilities)));
        putPage(map, page);
        return map;
    }

    private Map<String, Object> saveAutomation() {
        try {
            PluginAutomation automation = readAutomation();
            Map<String, Object> map = success();
            PluginAutomation saved = automationService().save(automation, null);
            List<PluginCapability> capabilities = capabilityStore().listAll();
            map.put("item", automationResponse(saved, pluginsById(), capabilitiesByKey(capabilities)));
            return map;
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
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
}
