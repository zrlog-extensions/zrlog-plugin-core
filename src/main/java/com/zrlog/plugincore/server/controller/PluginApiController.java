package com.zrlog.plugincore.server.controller;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.util.BooleanUtils;
import com.zrlog.plugincore.server.util.HttpMsgUtil;
import com.zrlog.plugincore.server.util.PluginUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PluginApiController extends Controller {

    public PluginApiController() {
    }

    public PluginApiController(HttpRequest request, HttpResponse response) {
        super(request, response);
    }

    private IOSession getSession() {
        return PluginConfig.getInstance().getIOSessionByPluginName(getRequest().getParaToStr("name"));
    }

    @ResponseBody
    public Map<String, Object> plugins() {
        Map<String, Object> map = new HashMap<>();
        map.put("plugins", PluginUtil.allRunningPlugins());
        map.put("dark", BooleanUtils.isTrue(getRequest().getHeader("Dark-Mode")));
        map.put("primaryColor", Objects.requireNonNullElse(getRequest().getHeader("Admin-Color-Primary"), "#1677ff"));
        map.put("pluginVersion", ConfigKit.get("version", ""));
        map.put("pluginBuildId", ConfigKit.get("buildId", ""));
        map.put("pluginBuildNumber", ConfigKit.get("buildNumber", ""));
        map.put("pluginCenter", "https://store.zrlog.com/plugin/index.html?upgrade-v3=true&from=#locationHref");
        return map;
    }

    private HttpRequestInfo genInfo() {
        return HttpMsgUtil.genInfo(getRequest());
    }

    @ResponseBody
    public Map<String, Object> stop() {
        Map<String, Object> map = new HashMap<>();
        if (getSession() != null) {
            String pluginName = getSession().getPlugin().getShortName();
            PluginUtil.stopPlugin(pluginName);
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

        if (getSession() != null) {
            map.put("code", 1);
            map.put("message", "插件已经启动了");
            return map;
        }
        if (RunConstants.runType != RunType.DEV) {
            String pluginName = getRequest().getParaToStr("name");
            PluginUtil.loadPlugin(PluginUtil.getPluginFile(pluginName), PluginCoreDAO.getInstance().getPluginVOByName(pluginName).getPlugin().getId());
            map.put("code", 0);
            map.put("message", "插件启动成功");
        } else {
            map.put("code", 1);
            map.put("message", "dev ENV");
        }
        return map;
    }

    @ResponseBody
    public Map<String, Object> uninstall() {
        IOSession session = getSession();
        String pluginName = getRequest().getParaToStr("name");
        if (session != null) {
            session.sendMsg(new MsgPacket(genInfo(), ContentType.JSON, MsgPacketStatus.SEND_REQUEST, IdUtil.getInt(), ActionType.PLUGIN_UNINSTALL.name()));
        }
        PluginUtil.deletePlugin(pluginName);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "移除成功");
        return map;
    }

    @ResponseBody
    public Map<String, Object> refreshCache() {
        PluginConfig.getInstance().getAllSessions().forEach(e -> {
            e.sendMsg(ContentType.JSON, new HashMap<>(), ActionType.REFRESH_CACHE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST);
        });
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "更新缓存成功");
        return map;
    }

    @ResponseBody
    public Map<String, Object> status() {
        List<String> plugins = PluginConfig.getInstance().getAllSessions().stream().map(e -> e.getPlugin().getShortName()).collect(Collectors.toList());
        if (PluginUtil.allRunning()) {
            return Map.of("code", 0, "status", "STARTED", "runningPlugins", plugins);
        }
        return Map.of("code", 0, "status", "STARTING", "runningPlugins", plugins);
    }
}
