package com.zrlog.plugincore.server.config;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PluginConfig {

    private static final PluginConfig instance = new PluginConfig();
    private File dbPropertiesFile;
    private RunType runType;
    private int masterPort;
    private String pluginBasePath;
    private final Map<String, IOSession> sessionMap = new ConcurrentHashMap<>();
    private BlogRunTime blogRunTime;

    public static PluginConfig getInstance() {
        return instance;
    }

    public static void init(RunType _runType, File _dbPropertiesFile, int masterPort, String pluginBasePath, BlogRunTime blogRunTime) {
        instance.runType = _runType;
        instance.dbPropertiesFile = _dbPropertiesFile;
        instance.masterPort = masterPort;
        instance.blogRunTime = blogRunTime;
        new File(pluginBasePath).mkdir();
        instance.pluginBasePath = pluginBasePath;

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

    public IOSession getIOSessionByPluginName(String pluginName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginInfoMap().get(pluginName);
        if (pluginVO != null) {
            return sessionMap.get(pluginVO.getPlugin().getId());
        }

        return null;
    }

    public IOSession getIOSessionByService(String service) {
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().getPluginInfoMap().values()) {
            if (pluginVO.getPlugin().getServices().contains(service)) {
                return sessionMap.get(pluginVO.getPlugin().getId());
            }
        }
        return null;
    }

    public Map<String, IOSession> getSessionMap() {
        return sessionMap;
    }

    public List<IOSession> getAllSessions() {
        return new ArrayList<>(sessionMap.values());
    }

    public BlogRunTime getBlogRunTime() {
        return blogRunTime;
    }
}
