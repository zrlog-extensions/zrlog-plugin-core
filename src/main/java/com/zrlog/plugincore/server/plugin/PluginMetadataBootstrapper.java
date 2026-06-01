package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class PluginMetadataBootstrapper {

    private static final long PLUGIN_METADATA_WAIT_TIMEOUT_MS = 30000L;
    private static final long PLUGIN_METADATA_WAIT_INTERVAL_MS = 100L;

    private final PluginProcessRuntime processRuntime;
    private final PluginSessionRegistry sessionRegistry;
    private final Consumer<String> pluginStopper;

    public PluginMetadataBootstrapper(PluginProcessRuntime processRuntime, Consumer<String> pluginStopper) {
        this(processRuntime, processRuntime == null ? new PluginSessionRegistry() : processRuntime.sessionRegistry(), pluginStopper);
    }

    public PluginMetadataBootstrapper(PluginProcessRuntime processRuntime, PluginSessionRegistry sessionRegistry,
                                      Consumer<String> pluginStopper) {
        this.processRuntime = processRuntime;
        this.sessionRegistry = sessionRegistry;
        this.pluginStopper = pluginStopper;
    }

    boolean startPluginFileForMetadata(File pluginFile) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0) {
            return false;
        }
        return startPluginFileForMetadata(pluginFile, resolvePluginId(pluginFile));
    }

    boolean startPluginFileForMetadata(File pluginFile, String pluginId) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0 || StringUtils.isEmpty(pluginId)) {
            return false;
        }
        String pluginShortName = PluginFiles.getPluginShortName(pluginFile);
        if (sessionRegistry.isRunningByPluginShortName(pluginShortName) && hasPluginFileChanged(pluginFile, pluginId)) {
            pluginStopper.accept(pluginShortName);
        }
        processRuntime.loadPlugin(pluginFile, pluginId);
        boolean registered = waitForPluginMetadata(pluginShortName, pluginId);
        if (registered) {
            PluginCoreDAO.getInstance().updatePluginFileMd5(pluginShortName, pluginId, PluginFiles.pluginFileMd5(pluginFile));
        }
        return registered;
    }

    boolean shouldStartPluginFileForMetadata(File pluginFile, String pluginId) {
        return shouldStartPluginFileForMetadata(pluginFile, pluginId, PluginCoreDAO.getInstance().loadSnapshot());
    }

    boolean shouldStartPluginFileForMetadata(File pluginFile, String pluginId, PluginCore pluginCore) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0 || StringUtils.isEmpty(pluginId)) {
            return false;
        }
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginCore, pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginCore, PluginFiles.getPluginShortName(pluginFile));
        }
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return true;
        }
        if (StringUtils.isEmpty(pluginVO.getFileMd5())) {
            return true;
        }
        return !Objects.equals(pluginVO.getFileMd5(), PluginFiles.pluginFileMd5(pluginFile));
    }

    File downloadAndStartPlugin(String fileName) throws Exception {
        File file = PluginFiles.downloadPlugin(fileName);
        if (!startPluginFileForMetadata(file)) {
            throw new RuntimeException("Download succeeded, but plugin metadata was not registered");
        }
        return file;
    }

    private String resolvePluginId(File pluginFile) {
        String pluginShortName = PluginFiles.getPluginShortName(pluginFile);
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginCore, pluginShortName);
        if (pluginVO != null && pluginVO.getPlugin() != null && !StringUtils.isEmpty(pluginVO.getPlugin().getId())) {
            return pluginVO.getPlugin().getId();
        }
        return UUID.randomUUID().toString();
    }

    private boolean waitForPluginMetadata(String pluginShortName, String pluginId) {
        long deadline = System.currentTimeMillis() + PLUGIN_METADATA_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            IOSession session = sessionRegistry.getLocalSessionByPluginId(pluginId);
            if (session == null) {
                session = sessionRegistry.getLocalSessionByPluginShortName(pluginShortName);
            }
            if (session != null && session.getPlugin() != null
                    && Objects.equals(pluginShortName, session.getPlugin().getShortName())) {
                return true;
            }
            try {
                Thread.sleep(PLUGIN_METADATA_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    PluginSessionRegistry sessionRegistry() {
        return sessionRegistry;
    }

    private boolean hasPluginFileChanged(File pluginFile, String pluginId) {
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginCore, pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginCore, PluginFiles.getPluginShortName(pluginFile));
        }
        if (pluginVO == null || StringUtils.isEmpty(pluginVO.getFileMd5())) {
            return false;
        }
        return !Objects.equals(pluginVO.getFileMd5(), PluginFiles.pluginFileMd5(pluginFile));
    }
}
