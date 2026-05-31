package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.plugin.PluginBootstrap;
import com.zrlog.plugincore.server.plugin.PluginSessions;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginIdleStopRunner {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginIdleStopRunner.class);

    private final PluginIdleStopPolicy idleStopPolicy = new PluginIdleStopPolicy();

    void stopIdlePlugins(long nowMs) {
        PluginRuntimeSetting runtimeSetting = PluginCoreDAO.getInstance().loadSnapshot().getSetting().getRuntime();
        stopIdlePlugins(nowMs, runtimeSetting);
    }

    public void stopIdlePlugins(long nowMs, PluginRuntimeSetting runtimeSetting) {
        if (!runtimeSetting.getOnDemandEnabled() || !runtimeSetting.getIdleStopEnabled()) {
            return;
        }
        long idleTimeoutMs = Math.max(1L, runtimeSetting.getIdleTimeoutSeconds()) * 1000L;
        PluginRuntimeStateStore stateStore = new PluginRuntimeStateStore(new WebsiteRuntimeKvStore());
        PluginRuntimeStateService stateService = new PluginRuntimeStateService(stateStore, new DefaultPluginRuntimeStarter());
        for (PluginRuntimeState state : stateStore.list()) {
            if (!idleStopPolicy.shouldStop(state, nowMs, idleTimeoutMs)) {
                continue;
            }
            PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(state.getPluginId());
            if (pluginVO == null || pluginVO.getPlugin() == null) {
                continue;
            }
            String pluginName = PluginSessions.nameOrShortName(pluginVO.getPlugin());
            stateService.markStopping(state.getPluginId(), pluginName);
            try {
                PluginBootstrap.stopPlugin(pluginVO.getPlugin().getShortName());
            } catch (RuntimeException e) {
                stateService.markFailed(state.getPluginId(), pluginName, e.getMessage());
                LOGGER.log(Level.WARNING, "stop idle plugin " + pluginVO.getPlugin().getShortName() + " error", e);
            }
        }
    }
}
