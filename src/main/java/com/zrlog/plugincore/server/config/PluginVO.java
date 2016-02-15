package com.zrlog.plugincore.server.config;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.type.PluginStatus;

public class PluginVO {

    private Plugin plugin;
    private PluginStatus status;

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public PluginStatus getStatus() {
        return status;
    }

    public void setStatus(PluginStatus status) {
        this.status = status;
    }
}
