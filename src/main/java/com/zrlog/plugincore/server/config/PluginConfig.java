package com.zrlog.plugincore.server.config;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.type.RunType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class PluginConfig {

    private static final PluginConfig instance = new PluginConfig();
    private File dbPropertiesFile;
    private RunType runType;
    private int masterPort;
    private String pluginBasePath;
    private BlogRunTime blogRunTime;

    public static PluginConfig getInstance() {
        return instance;
    }

    public static void init(RunType _runType, File _dbPropertiesFile, int masterPort, String pluginBasePath, BlogRunTime blogRunTime) {
        instance.runType = _runType;
        instance.dbPropertiesFile = _dbPropertiesFile;
        instance.masterPort = masterPort;
        instance.blogRunTime = blogRunTime;
        instance.pluginBasePath = resolvePluginBasePath(masterPort, pluginBasePath);
        new File(instance.pluginBasePath).mkdirs();

        try (FileInputStream fis = new FileInputStream(_dbPropertiesFile)) {
            Properties properties = new Properties();
            properties.load(fis);
            DataSourceWrapperImpl dataSource = new DataSourceWrapperImpl(properties, EnvKit.isDevMode());
            DAO.setDs(dataSource);
            String driverClass = properties.getProperty("driverClass");
            if (Objects.nonNull(driverClass)) {
                dataSource.setDriverClassName(driverClass);
            }
            dataSource.setJdbcUrl(properties.get("jdbcUrl").toString() + "&autoReconnect=true");
            dataSource.setPassword(properties.get("password").toString());
            dataSource.setUsername(properties.get("user").toString());
        } catch (IOException e) {
            LoggerUtil.getLogger(PluginConfig.class).log(Level.SEVERE, "", e);
        }
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
        return PluginConfig.getInstance().getPluginBasePath() + "/" + pluginShortName + "/usr/";
    }

    public String getPluginTempFolder(String pluginShortName) {
        if (EnvKit.isFaaSMode()) {
            return getFaaSRuntimeRoot(masterPort) + "/" + pluginShortName + "/tmp/";
        }
        return PluginConfig.getInstance().getPluginBasePath() + "/" + pluginShortName + "/tmp/";
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
