package com.zrlog.plugincore.server.controller;


import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.dao.WebSiteDAO;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingController extends Controller {

    @ResponseBody
    public Map<String, Object> load() throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("disableAutoDownloadLostFile", PluginCoreDAO.getInstance().getPluginCore().getSetting().isDisableAutoDownloadLostFile());
        map.put("commentPluginName", new WebSiteDAO().getWebSiteByNameIn(List.of("comment_plugin_name")).get("comment_plugin_name"));
        return map;
    }

    @ResponseBody
    public Map<String, Object> update() throws SQLException {
        Map<String, Object> map = new HashMap<>();
        PluginCoreDAO.getInstance().getPluginCore().getSetting().setDisableAutoDownloadLostFile(request.getParaToBool(
                "disableAutoDownloadLostFile"));
        String commentPlugin = request.getParaToStr("commentPluginName", "comment");
        new WebSiteDAO().saveOrUpdate("comment_plugin_name", commentPlugin);
        map.put("code", 0);
        map.put("message", "成功");
        return map;
    }
}
