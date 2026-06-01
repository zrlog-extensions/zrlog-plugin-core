package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessions;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginIdleStopRunner {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginIdleStopRunner.class);

    private final PluginIdleStopPolicy idleStopPolicy = new PluginIdleStopPolicy();
    private final PluginBootstrapService pluginBootstrapService;

    public PluginIdleStopRunner() {
        this(PluginRuntimeBridge.pluginBootstrap());
    }

    public PluginIdleStopRunner(PluginBootstrapService pluginBootstrapService) {
        this.pluginBootstrapService = pluginBootstrapService;
    }

    void stopIdlePlugins(long nowMs) {
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        stopIdlePlugins(nowMs, pluginCore.getSetting().getRuntime(), pluginCore);
    }

    public void stopIdlePlugins(long nowMs, PluginRuntimeSetting runtimeSetting) {
        stopIdlePlugins(nowMs, runtimeSetting, PluginCoreDAO.getInstance().loadSnapshot());
    }

    public void stopIdlePlugins(long nowMs, PluginRuntimeSetting runtimeSetting, PluginCore pluginCore) {
        if (!runtimeSetting.getOnDemandEnabled() || !runtimeSetting.getIdleStopEnabled()) {
            return;
        }
        long idleTimeoutMs = Math.max(1L, runtimeSetting.getIdleTimeoutSeconds()) * 1000L;
        PluginRuntimeStateStore stateStore = new PluginRuntimeStateStore(new WebsiteRuntimeKvStore());
        PluginRuntimeStateService stateService = new PluginRuntimeStateService(stateStore,
                new DefaultPluginRuntimeStarter(() -> pluginCore, pluginBootstrapService));
        for (PluginRuntimeState state : stateStore.list()) {
            if (!idleStopPolicy.shouldStop(state, nowMs, idleTimeoutMs)) {
                continue;
            }
            PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginCore, state.getPluginId());
            if (pluginVO == null || pluginVO.getPlugin() == null) {
                continue;
            }
            String pluginName = PluginSessions.nameOrShortName(pluginVO.getPlugin());
            stateService.markStopping(state.getPluginId(), pluginName);
            try {
                pluginBootstrapService.stopPlugin(pluginVO.getPlugin().getShortName());
            } catch (RuntimeException e) {
                stateService.markFailed(state.getPluginId(), pluginName, e.getMessage());
                LOGGER.log(Level.WARNING, "stop idle plugin " + pluginVO.getPlugin().getShortName() + " error", e);
            }
        }
    }
}
