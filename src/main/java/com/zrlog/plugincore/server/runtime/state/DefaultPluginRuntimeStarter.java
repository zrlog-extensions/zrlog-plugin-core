package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.plugin.PluginBootstrap;
import com.zrlog.plugincore.server.plugin.PluginFiles;
import com.zrlog.plugincore.server.plugin.PluginSessions;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class DefaultPluginRuntimeStarter implements PluginRuntimeStarter {

    private final Supplier<PluginCore> pluginCoreSupplier;

    public DefaultPluginRuntimeStarter() {
        this(() -> PluginCoreDAO.getInstance().loadSnapshot());
    }

    public DefaultPluginRuntimeStarter(PluginCore pluginCore) {
        this(() -> pluginCore);
    }

    DefaultPluginRuntimeStarter(Supplier<PluginCore> pluginCoreSupplier) {
        this.pluginCoreSupplier = pluginCoreSupplier;
    }

    @Override
    public boolean isStarted(String pluginId) {
        return PluginSessions.isRunningByPluginId(pluginId);
    }

    @Override
    public Optional<PluginIdentity> findPlugin(String pluginId) {
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().getPluginVOs(pluginCore())) {
            if (pluginVO.getPlugin() == null) {
                continue;
            }
            if (Objects.equals(pluginVO.getPlugin().getId(), pluginId)) {
                return Optional.of(new PluginIdentity(pluginVO.getPlugin().getId(),
                        pluginVO.getPlugin().getShortName(),
                        PluginSessions.nameOrShortName(pluginVO.getPlugin())));
            }
        }
        return Optional.empty();
    }

    @Override
    public void start(PluginIdentity identity) {
        boolean autoDownloadDisabled = pluginCore().getSetting().isDisableAutoDownloadLostFile();
        File pluginFile = PluginFiles.ensurePluginFile(identity.getPluginShortName(), autoDownloadDisabled);
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0) {
            throw new RuntimeException(PluginFiles.missingPluginFileMessage(identity.getPluginShortName(), autoDownloadDisabled));
        }
        PluginBootstrap.loadPlugin(pluginFile, identity.getPluginId());
    }

    private PluginCore pluginCore() {
        PluginCore pluginCore = pluginCoreSupplier.get();
        return pluginCore == null ? new PluginCore() : pluginCore;
    }

    @Override
    public String runtimeMode(PluginIdentity identity) {
        File pluginFile = PluginFiles.getAvailablePluginFile(identity.getPluginShortName());
        if (pluginFile.getName().endsWith(".jar")) {
            return "process";
        }
        return "native";
    }

    @Override
    public boolean managesRuntimeState() {
        return true;
    }

    @Override
    public void cleanupStartFailure(PluginIdentity identity) {
        PluginBootstrap.stopPlugin(identity.getPluginShortName());
    }
}
