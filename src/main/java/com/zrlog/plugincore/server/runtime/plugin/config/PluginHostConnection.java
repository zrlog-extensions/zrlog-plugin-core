package com.zrlog.plugincore.server.runtime.plugin.config;

public class PluginHostConnection {

    public static final String DEFAULT_BLOG_API_HOME_URL = "http://localhost:7080";

    private volatile String blogPluginToken = "";
    private volatile String blogApiHomeUrl = DEFAULT_BLOG_API_HOME_URL;
    private volatile String nativeInfo = "";

    public PluginHostConnection() {
    }

    public void configure(String apiHomeUrl, String pluginToken, String nativeInfo) {
        this.blogApiHomeUrl = apiHomeUrl;
        this.blogPluginToken = pluginToken;
        this.nativeInfo = nativeInfo;
    }

    public String getBlogPluginToken() {
        return blogPluginToken;
    }

    public String getBlogApiHomeUrl() {
        return blogApiHomeUrl;
    }

    public String getNativeInfo() {
        return nativeInfo;
    }
}
