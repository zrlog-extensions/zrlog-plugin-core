package com.zrlog.plugincore.server.controller;


import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCoreSetting;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;

import java.util.HashMap;
import java.util.Map;

public class SettingController extends Controller {

    @ResponseBody
    public PluginCoreSetting load() {
        return PluginCoreDAO.getInstance().getPluginCore().getSetting();
    }

    @ResponseBody
    public Map<String, Object> update() {
        Map<String, Object> map = new HashMap<>();
        PluginCoreDAO.getInstance().getPluginCore().getSetting().setDisableAutoDownloadLostFile(request.getParaToBool(
                "disableAutoDownloadLostFile"));
        map.put("code", 0);
        map.put("message", "成功");
        return map;
    }
}
