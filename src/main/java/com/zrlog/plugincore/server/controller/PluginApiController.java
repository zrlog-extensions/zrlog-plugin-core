package com.zrlog.plugincore.server.controller;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.RuntimeEvents;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginCoreSetting;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.plugin.PluginBootstrap;
import com.zrlog.plugincore.server.plugin.PluginSessions;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.RuntimeCapabilityInvokerFactory;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventHandlerResolver;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventPublishResult;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventRequest;
import com.zrlog.plugincore.server.runtime.event.RuntimeEventRuntime;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogStore;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;
import com.zrlog.plugincore.server.util.AdminTheme;
import com.zrlog.plugincore.server.util.HttpMsgUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PluginApiController extends Controller {

    public PluginApiController() {
    }

    public PluginApiController(HttpRequest request, HttpResponse response) {
        super(request, response);
    }

    private IOSession getSession() {
        return PluginSessions.getLocalSessionByPluginShortName(getRequest().getParaToStr("name"));
    }

    @ResponseBody
    public Map<String, Object> plugins() {
        AdminTheme adminTheme = AdminTheme.fromRequest(getRequest());
        PluginCore pluginCore = Application.isNativeAgent() ? null : PluginCoreDAO.getInstance().loadSnapshot();
        Map<String, Object> map = new HashMap<>();
        map.put("plugins", pluginsForCurrentMode(pluginCore));
        map.put("setting", pluginCore == null ? new PluginCoreSetting() : pluginCore.getSetting());
        map.put("dark", adminTheme.isDarkMode());
        map.put("primaryColor", adminTheme.getAdminColorPrimary());
        map.put("pluginVersion", ConfigKit.get("version", ""));
        map.put("pluginBuildId", ConfigKit.get("buildId", ""));
        map.put("pluginBuildNumber", ConfigKit.get("buildNumber", ""));
        map.put("requiredPlugins", PluginBootstrap.getRequiredPlugins().keySet());
        map.put("pluginCenter", "https://store.zrlog.com/plugin/index.html?upgrade-v3=true&from=#locationHref");
        return map;
    }

    static List<?> pluginsForCurrentMode() {
        return Application.isNativeAgent() ? Collections.emptyList() : pluginsForCurrentMode(PluginCoreDAO.getInstance().loadSnapshot());
    }

    static List<?> pluginsForCurrentMode(PluginCore pluginCore) {
        if (Application.isNativeAgent() || pluginCore == null || pluginCore.getPluginInfoMap() == null) {
            return Collections.emptyList();
        }
        return pluginCore.getPluginInfoMap().values().stream()
                .filter(pluginEntry -> pluginEntry.getPlugin() != null)
                .map(pluginEntry -> {
                    if (pluginEntry.getPlugin().getPreviewImageBase64() == null
                            || pluginEntry.getPlugin().getPreviewImageBase64().isEmpty()) {
                        pluginEntry.getPlugin().setPreviewImageBase64("");
                    }
                    return pluginEntry.getPlugin();
                })
                .collect(Collectors.toList());
    }

    private HttpRequestInfo genInfo() {
        return HttpMsgUtil.genInfo(getRequest());
    }

    @ResponseBody
    public Map<String, Object> stop() {
        Map<String, Object> map = new HashMap<>();
        if (getSession() != null) {
            String pluginShortName = getSession().getPlugin().getShortName();
            PluginBootstrap.stopPlugin(pluginShortName);
            map.put("code", 0);
            map.put("message", "停止成功");
        } else {
            map.put("code", 1);
            map.put("message", "插件没有启动");
        }
        return map;

    }

    @ResponseBody
    public Map<String, Object> start() throws IOException {
        Map<String, Object> map = new HashMap<>();

        String pluginShortName = getRequest().getParaToStr("name");
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            map.put("code", 1);
            map.put("message", "插件不存在");
            return map;
        }
        String pluginId = pluginVO.getPlugin().getId();
        if (PluginSessions.isRunningByPluginId(pluginId)) {
            map.put("code", 1);
            map.put("message", "插件已经启动了");
            return map;
        }
        boolean started = runtimeStateService().ensureStarted(pluginId);
        map.put("code", started ? 0 : 1);
        map.put("message", started ? "插件启动成功" : "插件启动失败");
        return map;
    }

    @ResponseBody
    public Map<String, Object> uninstall() {
        IOSession session = getSession();
        String pluginShortName = getRequest().getParaToStr("name");
        if (PluginBootstrap.getRequiredPlugins().containsKey(pluginShortName)) {
            Map<String, Object> map = new HashMap<>();
            map.put("code", 1);
            map.put("message", "必要插件，无法移除");
            return map;
        }
        if (session != null) {
            session.sendMsg(new MsgPacket(genInfo(), ContentType.JSON, MsgPacketStatus.SEND_REQUEST, IdUtil.getInt(), ActionType.PLUGIN_UNINSTALL.name()));
        }
        PluginBootstrap.deletePlugin(pluginShortName);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "移除成功");
        return map;
    }

    @ResponseBody
    public Map<String, Object> refreshCache() {
        WebsiteRuntimeKvStore kvStore = new WebsiteRuntimeKvStore();
        RuntimeEventRequest eventRequest = refreshCacheRequest();
        long startedAtMs = System.currentTimeMillis();
        RuntimeEventPublishResult eventResult = runtimeEventRuntime(kvStore).publish(eventRequest);
        int legacySessionCount = broadcastLegacyRefreshCache(eventResult.getHandlerPluginIds());
        int failedCount = eventResult.getFailedCount();
        int successCount = eventResult.getSuccessCount() + legacySessionCount;
        new InvocationLogStore(kvStore).append(refreshCacheInvocationLog(eventRequest, eventResult, startedAtMs, System.currentTimeMillis()));

        Map<String, Object> map = new HashMap<>();
        map.put("code", failedCount == 0 ? 0 : 1);
        map.put("message", failedCount == 0 ? "更新缓存成功" : "部分插件更新缓存失败");
        map.put("runtimeEventSuccessCount", eventResult.getSuccessCount());
        map.put("runtimeEventFailedCount", failedCount);
        map.put("runtimeEventHandlerCount", eventResult.getHandlerCount());
        map.put("legacySessionCount", legacySessionCount);
        map.put("successCount", successCount);
        return map;
    }

    private int broadcastLegacyRefreshCache(Set<String> runtimeEventHandlerPluginIds) {
        int[] count = new int[]{0};
        PluginSessions.getAllLocalSessions().forEach(e -> {
            if (e.getPlugin() != null && runtimeEventHandlerPluginIds != null
                    && runtimeEventHandlerPluginIds.contains(e.getPlugin().getId())) {
                return;
            }
            e.sendMsg(ContentType.JSON, new HashMap<>(), ActionType.REFRESH_CACHE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST);
            count[0]++;
        });
        return count[0];
    }

    private RuntimeEventRequest refreshCacheRequest() {
        RuntimeEventRequest request = new RuntimeEventRequest();
        request.setEventType(RuntimeEvents.REFRESH_CACHE);
        request.setAliases(Arrays.asList(RuntimeEvents.LEGACY_REFRESH_CACHE, ActionType.REFRESH_CACHE.name()));
        request.setSource("plugin-core");
        Map<String, Object> payload = new HashMap<>();
        payload.put("actionType", ActionType.REFRESH_CACHE.name());
        request.setPayload(payload);
        return request;
    }

    static CapabilityInvocationLog refreshCacheInvocationLog(RuntimeEventRequest request,
                                                             RuntimeEventPublishResult result,
                                                             long startedAtMs,
                                                             long finishedAtMs) {
        CapabilityInvocationLog log = new CapabilityInvocationLog();
        log.setId(UUID.randomUUID().toString());
        log.setPluginId("__system__");
        log.setCapabilityKey(RuntimeEvents.REFRESH_CACHE);
        log.setSource(RuntimeEventRuntime.SOURCE);
        log.setRequestId(request == null ? null : request.getRequestId());
        log.setTraceId(request == null ? null : request.getTraceId());
        log.setStartedAt(startedAtMs);
        log.setFinishedAt(finishedAtMs);
        log.setDurationMs(finishedAtMs - startedAtMs);
        int failedCount = result == null ? 0 : result.getFailedCount();
        log.setStatus(failedCount == 0 ? "success" : "error");
        if (failedCount > 0) {
            log.setErrorMessage("Runtime event handlers failed: " + failedCount);
        }
        return log;
    }

    private RuntimeEventRuntime runtimeEventRuntime(KvRepository kvStore) {
        return new RuntimeEventRuntime(
                new CapabilityStore(kvStore),
                new RuntimeEventHandlerResolver(),
                RuntimeCapabilityInvokerFactory.socket(kvStore)
        );
    }

    @ResponseBody
    public Map<String, Object> status() {
        List<String> plugins = PluginSessions.getAllLocalSessions().stream().map(e -> e.getPlugin().getShortName()).collect(Collectors.toList());
        boolean onDemandEnabled = Boolean.TRUE.equals(PluginCoreDAO.getInstance().loadSnapshot().getSetting().getRuntime().getOnDemandEnabled());
        boolean allRunning = !onDemandEnabled && PluginBootstrap.allRunning();
        return statusResponse(onDemandEnabled, allRunning, plugins);
    }

    static Map<String, Object> statusResponse(boolean onDemandEnabled, boolean allRunning, List<String> runningPlugins) {
        String status = onDemandEnabled || allRunning ? "STARTED" : "STARTING";
        return Map.of("code", 0, "status", status, "runningPlugins", runningPlugins);
    }

    private PluginRuntimeStateService runtimeStateService() {
        return new PluginRuntimeStateService(new PluginRuntimeStateStore(new WebsiteRuntimeKvStore()), new DefaultPluginRuntimeStarter());
    }
}
