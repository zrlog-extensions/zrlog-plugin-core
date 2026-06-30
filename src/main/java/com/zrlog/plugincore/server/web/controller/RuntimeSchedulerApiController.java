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

import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.ActionResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.AutomationResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.AutomationRunResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.AutomationsResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.ItemResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.PageResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.Response;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.ResultResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiModels.SchedulerSettingsResponse;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.*;

public class RuntimeSchedulerApiController extends RuntimeBaseApiController {

    private final Gson gson = new Gson();

    @ResponseBody
    public SchedulerSettingsResponse schedulerSettings() {
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
        SchedulerSettingsResponse response = new SchedulerSettingsResponse();
        response.setEnabled(setting.getEnabled());
        response.setExternalHost(setting.getExternalHost());
        response.setEffectiveExternalHost(SchedulerExternalEndpoint.effectiveHost(setting.getExternalHost(), fallbackHomeUrl));
        response.setExternalTickPath(SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH);
        response.setExternalTickUrl(SchedulerExternalEndpoint.tickUrl(setting.getExternalHost(), fallbackHomeUrl));
        response.setProviders(setting.getProviders());
        response.setSystemTimezone(ZoneId.systemDefault().getId());
        return response;
    }

    @ResponseBody
    public Response automations() {
        if (getRequest().getMethod() == HttpMethod.POST) {
            return saveAutomation();
        }
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        List<PluginCapability> capabilities = capabilityStore().listAll();
        return new AutomationsResponse(automationResponses(
                automationService(pluginCore.getSetting().getRuntime()).listWithSystemAutomations(),
                pluginsById(pluginCore),
                capabilitiesByKey(capabilities)), ZoneId.systemDefault().getId());
    }

    @ResponseBody
    public Response automationUpdate() {
        return saveAutomation();
    }

    @ResponseBody
    public ActionResponse automationDelete() {
        boolean removed = automationService().delete(getRequest().getParaToStr("id"));
        return ActionResponse.removed(removed);
    }

    @ResponseBody
    public Response automationRunNow() {
        try {
            PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
            PluginAutomationRun run = schedulerTickService(pluginCore).runNow(getRequest().getParaToStr("id"), ZonedDateTime.now());
            List<PluginCapability> capabilities = capabilityStore().listAll();
            return new ItemResponse<AutomationRunResponse>(
                    automationRunResponse(run, pluginsById(pluginCore), capabilitiesByKey(capabilities)));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Response schedulerTick() {
        try {
            SchedulerTickResult result = schedulerTickService(PluginCoreDAO.getInstance().loadSnapshot())
                    .tick(ZonedDateTime.now(), RuntimeSources.TICK);
            return new ResultResponse(result);
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public PageResponse<AutomationRunResponse> automationRuns() {
        PageData<PluginAutomationRun> page = newestPage(automationRunStore().list(), 8);
        List<PluginCapability> capabilities = capabilityStore().listAll();
        return pageResponse(automationRunResponses(page.getRows(), pluginsById(), capabilitiesByKey(capabilities)), page);
    }

    private Response saveAutomation() {
        try {
            PluginAutomation automation = readAutomation();
            PluginAutomation saved = automationService().save(automation, null);
            List<PluginCapability> capabilities = capabilityStore().listAll();
            return new ItemResponse<AutomationResponse>(automationResponse(saved, pluginsById(), capabilitiesByKey(capabilities)));
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
