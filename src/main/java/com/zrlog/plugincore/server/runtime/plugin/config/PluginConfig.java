package com.zrlog.plugincore.server.runtime.plugin.config;

import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.type.RunType;

import java.io.File;

public class PluginConfig {

    private File dbPropertiesFile;
    private RunType runType;
    private int masterPort;
    private String pluginBasePath;
    private BlogRunTime blogRunTime;

    public void configure(RunType _runType, File _dbPropertiesFile, int masterPort, String pluginBasePath, BlogRunTime blogRunTime) {
        this.runType = _runType;
        this.dbPropertiesFile = _dbPropertiesFile;
        this.masterPort = masterPort;
        this.blogRunTime = blogRunTime;
        this.pluginBasePath = resolvePluginBasePath(masterPort, pluginBasePath);
        new File(this.pluginBasePath).mkdirs();
    }


    public File getDbPropertiesFile() {
        return dbPropertiesFile;
    }

    public RunType getRunType() {
        return runType;
    }

    public int getMasterPort() {
        return masterPort;
    }

    public String getPluginBasePath() {
        return pluginBasePath;
    }

    public String getPluginHomeFolder(String pluginShortName) {
        if (EnvKit.isFaaSMode()) {
            return getFaaSRuntimeRoot(masterPort) + "/" + pluginShortName + "/usr/";
        }
        return pluginBasePath + "/" + pluginShortName + "/usr/";
    }

    public String getPluginTempFolder(String pluginShortName) {
        if (EnvKit.isFaaSMode()) {
            return getFaaSRuntimeRoot(masterPort) + "/" + pluginShortName + "/tmp/";
        }
        return pluginBasePath + "/" + pluginShortName + "/tmp/";
    }

    static String resolvePluginBasePath(int masterPort, String pluginBasePath) {
        return pluginBasePath;
    }

    public static String getFaaSRuntimeRoot(int masterPort) {
        return "/tmp/" + masterPort;
    }

    public BlogRunTime getBlogRunTime() {
        return blogRunTime;
    }
}
