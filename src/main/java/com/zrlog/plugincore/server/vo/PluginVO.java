package com.zrlog.plugincore.server.vo;

import com.zrlog.plugin.message.Plugin;

public class PluginVO {

    private Plugin plugin;
    private String fileMd5;

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }
}
