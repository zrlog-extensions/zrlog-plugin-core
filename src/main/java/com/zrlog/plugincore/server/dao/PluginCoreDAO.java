package com.zrlog.plugincore.server.dao;

import com.google.gson.Gson;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PluginCoreDAO {

    private static final String PLUGIN_DB_KEY = "plugin_core_db_key";

    private final PluginCore pluginCore;

    private static PluginCoreDAO instance;

    private static final Lock initLock = new ReentrantLock();

    public static PluginCoreDAO getInstance() {
        if (Objects.nonNull(instance)) {
            return instance;
        }
        initLock.lock();
        try {
            if (Objects.isNull(instance)) {
                instance = new PluginCoreDAO();
            }
            return instance;
        } finally {
            initLock.unlock();
        }
    }

    private PluginCore getPluginCoreByDb() {
        try {
            String text = (String) new WebSiteDAO().set("name", PLUGIN_DB_KEY).queryFirst("value");
            if (text != null && !text.isEmpty()) {
                return new Gson().fromJson(text, PluginCore.class);
            } else {
                return new Gson().fromJson("{}", PluginCore.class);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PluginCoreDAO() {
        this.pluginCore = getPluginCoreByDb();
        saveToDbThread();
    }

    public PluginCore getPluginCore() {
        return pluginCore;
    }

    public List<Plugin> getPlugins() {
        return PluginCoreDAO.getInstance().getPluginCore().getPluginInfoMap().values().stream().map(PluginVO::getPlugin).collect(Collectors.toList());
    }

    public Map<String, PluginVO> getPluginInfoMap() {
        return pluginCore.getPluginInfoMap();
    }

    public PluginVO getPluginVOByName(String pluginName) {
        return pluginCore.getPluginInfoMap().get(pluginName);
    }

    public Collection<PluginVO> getAllPluginVO() {
        if (pluginCore != null && pluginCore.getPluginInfoMap() != null) {
            return pluginCore.getPluginInfoMap().values();
        }
        return new ArrayList<>();
    }

    private void saveToDbThread() {
        new Thread(() -> {
            String currentPluginText = "";
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    LoggerUtil.getLogger(PluginConfig.class).log(Level.SEVERE, "stop", e);
                }
                if (Objects.isNull(pluginCore)) {
                    return;
                }
                String jsonStr = new Gson().toJson(pluginCore);
                if (currentPluginText.equals(jsonStr)) {
                    continue;
                }
                currentPluginText = jsonStr;
                try {
                    new WebSiteDAO().saveOrUpdate(PLUGIN_DB_KEY, currentPluginText);
                } catch (SQLException e) {
                    LoggerUtil.getLogger(PluginConfig.class).log(Level.SEVERE, "", e);
                }
            }
        }).start();
    }
}
