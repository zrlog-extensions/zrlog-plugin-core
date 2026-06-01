package com.zrlog.plugincore.server.runtime.plugin.config;

public class PluginHostConnection {

    public static final String DEFAULT_BLOG_API_HOME_URL = "http://localhost:7080";

    private final String blogPluginToken;
    private final String blogApiHomeUrl;
    private final String nativeInfo;

    public PluginHostConnection(String apiHomeUrl, String pluginToken, String nativeInfo) {
        this.blogApiHomeUrl = apiHomeUrl;
        this.blogPluginToken = pluginToken;
        this.nativeInfo = nativeInfo;
    }

    public static PluginHostConnection defaults() {
        return new PluginHostConnection(DEFAULT_BLOG_API_HOME_URL, "", "");
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
