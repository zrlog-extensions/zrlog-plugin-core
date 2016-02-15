package com.zrlog.plugincore.server.config;

import java.util.HashMap;
import java.util.Map;

public class PluginCore {

    private Map<String, PluginVO> pluginInfoMap = new HashMap<>();
    private PluginCoreSetting setting = new PluginCoreSetting();

    public Map<String, PluginVO> getPluginInfoMap() {
        return pluginInfoMap;
    }

    public void setPluginInfoMap(Map<String, PluginVO> pluginInfoMap) {
        this.pluginInfoMap = pluginInfoMap;
    }

    public PluginCoreSetting getSetting() {
        return setting;
    }

    public void setSetting(PluginCoreSetting setting) {
        this.setting = setting;
    }
}
