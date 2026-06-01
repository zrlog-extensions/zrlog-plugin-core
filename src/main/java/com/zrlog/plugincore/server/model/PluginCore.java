package com.zrlog.plugincore.server.model;

import com.zrlog.plugincore.server.vo.PluginCoreSetting;
import com.zrlog.plugincore.server.vo.PluginVO;

import java.util.LinkedHashMap;
import java.util.Map;

public class PluginCore {

    private Map<String, PluginVO> pluginInfoMap = new LinkedHashMap<>();
    private PluginCoreSetting setting = new PluginCoreSetting();

    public Map<String, PluginVO> getPluginInfoMap() {
        return pluginInfoMap;
    }

    public void setPluginInfoMap(Map<String, PluginVO> pluginInfoMap) {
        this.pluginInfoMap = pluginInfoMap;
    }

    public PluginCoreSetting getSetting() {
        if (setting == null) {
            setting = new PluginCoreSetting();
        }
        return setting;
    }

    public void setSetting(PluginCoreSetting setting) {
        this.setting = setting;
    }
}
