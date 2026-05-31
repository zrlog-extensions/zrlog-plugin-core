package com.zrlog.plugincore.server.runtime.state;

public class PluginIdentity {

    private String pluginId;
    private String pluginShortName;
    private String pluginName;

    public PluginIdentity() {
    }

    public PluginIdentity(String pluginId, String pluginShortName) {
        this(pluginId, pluginShortName, pluginShortName);
    }

    public PluginIdentity(String pluginId, String pluginShortName, String pluginName) {
        this.pluginId = pluginId;
        this.pluginShortName = pluginShortName;
        this.pluginName = pluginName;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginShortName() {
        return pluginShortName;
    }

    public void setPluginShortName(String pluginShortName) {
        this.pluginShortName = pluginShortName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }
}
