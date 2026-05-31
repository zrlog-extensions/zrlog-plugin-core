package com.zrlog.plugincore.server.dao;

import com.google.gson.Gson;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;

import java.util.*;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PluginCoreDAO {

    private static final String PLUGIN_DB_KEY = "plugin_core_db_key";
    private static final int UPDATE_RETRIES = 3;
    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreDAO.class);

    private final Gson gson = new Gson();

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

    private Optional<String> getPluginCoreRawByDb() {
        try {
            DaoTrace.info(LOGGER, "pluginCore.queryRaw", "key=" + PLUGIN_DB_KEY);
            String text = (String) new WebSiteDAO().queryValueByName(PLUGIN_DB_KEY);
            return text == null ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PluginCore parsePluginCore(Optional<String> text) {
        if (text.isPresent() && !text.get().isEmpty()) {
            return normalize(gson.fromJson(text.get(), PluginCore.class));
        }
        return new PluginCore();
    }

    public synchronized PluginCore loadSnapshot() {
        DaoTrace.info(LOGGER, "pluginCore.loadSnapshot", null);
        PluginCore pluginCore = parsePluginCore(getPluginCoreRawByDb());
        PluginRuntimeStates.cleanupDirtyRuntimeStates(pluginCore);
        return pluginCore;
    }

    public synchronized PluginCore update(Consumer<PluginCore> consumer) {
        DaoTrace.info(LOGGER, "pluginCore.update", "maxRetries=" + UPDATE_RETRIES);
        for (int i = 0; i < UPDATE_RETRIES; i++) {
            DaoTrace.info(LOGGER, "pluginCore.updateAttempt", "attempt=" + (i + 1));
            Optional<String> raw = getPluginCoreRawByDb();
            PluginCore nextPluginCore = parsePluginCore(raw);
            consumer.accept(nextPluginCore);
            String json = gson.toJson(nextPluginCore);
            try {
                if (new WebSiteDAO().compareAndSet(PLUGIN_DB_KEY, raw.orElse(null), json)) {
                    return nextPluginCore;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("Failed to update plugin core due to concurrent modification");
    }

    public List<Plugin> getPlugins() {
        DaoTrace.info(LOGGER, "pluginCore.getPlugins", null);
        return loadSnapshot().getPluginInfoMap().values().stream().map(PluginVO::getPlugin).collect(Collectors.toList());
    }

    public Map<String, PluginVO> getPluginInfoMap() {
        DaoTrace.info(LOGGER, "pluginCore.getPluginInfoMap", null);
        return loadSnapshot().getPluginInfoMap();
    }

    public Collection<PluginVO> getPluginVOs() {
        DaoTrace.info(LOGGER, "pluginCore.getPluginVOs", null);
        PluginCore pluginCore = loadSnapshot();
        if (pluginCore.getPluginInfoMap() == null) {
            return Collections.emptyList();
        }
        return pluginCore.getPluginInfoMap().values();
    }

    public PluginVO getPluginVOByShortName(String pluginShortName) {
        DaoTrace.info(LOGGER, "pluginCore.getPluginVOByShortName", "pluginShortName=" + pluginShortName);
        if (isBlank(pluginShortName)) {
            return null;
        }
        PluginCore pluginCore = loadSnapshot();
        PluginVO pluginVO = pluginCore.getPluginInfoMap().get(pluginShortName);
        if (pluginVO != null) {
            return pluginVO;
        }
        for (PluginVO item : pluginCore.getPluginInfoMap().values()) {
            if (item != null && item.getPlugin() != null
                    && Objects.equals(pluginShortName, item.getPlugin().getShortName())) {
                return item;
            }
        }
        return null;
    }

    public PluginVO getPluginVOById(String pluginId) {
        DaoTrace.info(LOGGER, "pluginCore.getPluginVOById", "pluginId=" + pluginId);
        for (PluginVO pluginVO : getPluginVOs()) {
            if (pluginVO.getPlugin() != null && Objects.equals(pluginId, pluginVO.getPlugin().getId())) {
                return pluginVO;
            }
        }
        return null;
    }

    public void updatePluginFileMd5(String pluginShortName, String pluginId, String fileMd5) {
        DaoTrace.info(LOGGER, "pluginCore.updatePluginFileMd5",
                "pluginShortName=" + pluginShortName + " pluginId=" + pluginId + " fileMd5=" + DaoTrace.valueSummary(fileMd5));
        if (isBlank(fileMd5)) {
            return;
        }
        PluginVO pluginVO = getPluginVOById(pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            pluginVO = getPluginVOByShortName(pluginShortName);
        }
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return;
        }
        PluginVO finalPluginVO = pluginVO;
        update(pluginCore -> {
            removeAliasEntriesByShortName(pluginCore, pluginShortName);
            PluginVO current = pluginCore.getPluginInfoMap().get(pluginShortName);
            if (current == null || current.getPlugin() == null
                    || !Objects.equals(pluginId, current.getPlugin().getId())) {
                current = finalPluginVO;
            }
            current.setFileMd5(fileMd5);
            pluginCore.getPluginInfoMap().put(pluginShortName, current);
        });
    }

    public void removePluginByShortName(String pluginShortName) {
        DaoTrace.info(LOGGER, "pluginCore.removePluginByShortName", "pluginShortName=" + pluginShortName);
        if (isBlank(pluginShortName)) {
            return;
        }
        update(pluginCore -> {
            removeAliasEntriesByShortName(pluginCore, pluginShortName);
            pluginCore.getPluginInfoMap().remove(pluginShortName);
        });
    }

    private void removeAliasEntriesByShortName(PluginCore pluginCore, String pluginShortName) {
        pluginCore.getPluginInfoMap().entrySet().removeIf(entry -> !Objects.equals(entry.getKey(), pluginShortName)
                && entry.getValue() != null
                && entry.getValue().getPlugin() != null
                && Objects.equals(pluginShortName, entry.getValue().getPlugin().getShortName()));
    }

    private PluginCore normalize(PluginCore pluginCore) {
        return pluginCore == null ? new PluginCore() : pluginCore;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
