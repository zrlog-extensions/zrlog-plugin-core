package com.zrlog.plugincore.server.web.controller;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.PluginCoreRunMode;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.plugin.process.PluginProcessQueryService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;
import com.zrlog.plugincore.server.vo.PluginVO;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.*;

public class RuntimeStateApiController extends RuntimeBaseApiController {

    @ResponseBody
    public Map<String, Object> runtimeStates() {
        Map<String, Object> map = success();
        map.put("items", runtimeStatesForCurrentMode());
        return map;
    }

    @ResponseBody
    public Map<String, Object> runtimeStart() {
        String pluginId = getRequest().getParaToStr("pluginId");
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginCore, pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return error("插件不存在");
        }
        boolean started = runtimeStateService(pluginCore).ensureStarted(pluginVO.getPlugin().getId());
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
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginCore, pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return error("插件不存在");
        }
        if (activeInvocationCount(pluginId) > 0) {
            return error("插件正在执行任务");
        }
        String pluginName = pluginDisplayName(pluginVO.getPlugin());
        try {
            runtimeStateService(pluginCore).markStopping(pluginId, pluginName);
            pluginBootstrap().stopPlugin(pluginVO.getPlugin().getShortName());
            runtimeStateService(pluginCore).markStopped(pluginId, pluginName);
            return success();
        } catch (RuntimeException e) {
            runtimeStateService(pluginCore).markFailed(pluginId, pluginName, e.getMessage());
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> runtimeSettings() {
        PluginCoreDAO pluginCoreDAO = PluginCoreDAO.getInstance();
        PluginCore currentPluginCore = pluginCoreDAO.loadSnapshot();
        PluginRuntimeSetting setting = currentPluginCore.getSetting().getRuntime();
        if (getRequest().getMethod() == HttpMethod.POST) {
            try {
                Boolean onDemandEnabled = runtimeBooleanParam("onDemandEnabled", setting.getOnDemandEnabled());
                Boolean autoDownloadMissingPluginFileEnabled = runtimeBooleanParam("autoDownloadMissingPluginFileEnabled",
                        currentPluginCore.getSetting().isAutoDownloadMissingPluginFileEnabled());
                Boolean idleStopEnabled = runtimeBooleanParam("idleStopEnabled", setting.getIdleStopEnabled());
                Long idleTimeoutSeconds = runtimeLongParam("idleTimeoutSeconds", setting.getIdleTimeoutSeconds(), 10L);
                Long idleScanIntervalSeconds = runtimeLongParam("idleScanIntervalSeconds", setting.getIdleScanIntervalSeconds(), 5L);
                setting = pluginCoreDAO.update(pluginCore -> {
                    pluginCore.getSetting().setAutoDownloadMissingPluginFileEnabled(autoDownloadMissingPluginFileEnabled);
                    PluginRuntimeSetting runtime = pluginCore.getSetting().getRuntime();
                    runtime.setOnDemandEnabled(onDemandEnabled);
                    runtime.setIdleStopEnabled(idleStopEnabled);
                    runtime.setIdleTimeoutSeconds(idleTimeoutSeconds);
                    runtime.setIdleScanIntervalSeconds(idleScanIntervalSeconds);
                }).getSetting().getRuntime();
                pluginBootstrap().loadPluginsAsync();
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }
        }
        return runtimeSettingsResponse(setting);
    }

    @ResponseBody
    public Map<String, Object> invocationLogs() {
        Map<String, Object> map = success();
        PageData<CapabilityInvocationLog> page = newestPage(invocationLogStore().list(), 10);
        map.put("rows", invocationLogResponses(page.getRows(), pluginsById()));
        putPage(map, page);
        return map;
    }

    @ResponseBody
    public Map<String, Object> capabilities() {
        String pluginId = getRequest().getParaToStr("pluginId");
        String type = getRequest().getParaToStr("type");
        String exposure = getRequest().getParaToStr("exposure");
        List<PluginCapability> items = capabilityStore().listAll();
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

    static List<?> runtimeStatesForCurrentMode() {
        return PluginCoreRunMode.isNativeAgent() ? Collections.emptyList() : new PluginProcessQueryService().query();
    }

    private Map<String, Object> runtimeSettingsResponse(PluginRuntimeSetting setting) {
        Map<String, Object> map = success();
        map.put("onDemandEnabled", setting.getOnDemandEnabled());
        map.put("autoDownloadMissingPluginFileEnabled", setting.getAutoDownloadMissingPluginFileEnabled());
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
}
