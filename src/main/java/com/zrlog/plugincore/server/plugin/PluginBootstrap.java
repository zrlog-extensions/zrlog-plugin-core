package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginBootstrap {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginBootstrap.class);
    private static final long PLUGIN_METADATA_WAIT_TIMEOUT_MS = 30000L;
    private static final long PLUGIN_METADATA_WAIT_INTERVAL_MS = 100L;
    private static final Map<String, String> REQUIRED_PLUGINS = new HashMap<>();
    private static final AtomicBoolean PLUGIN_BOOTSTRAP_RUNNING = new AtomicBoolean(false);
    private static final ExecutorService PLUGIN_BOOTSTRAP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "zrlog-plugin-bootstrap");
        thread.setDaemon(true);
        return thread;
    });

    private static PluginScanRunnable pluginScanRunnable;

    static {
        REQUIRED_PLUGINS.put("comment", "comment");
    }

    private PluginBootstrap() {
    }

    public static void verifyPluginCoreReadable() {
        PluginCoreDAO.getInstance().loadSnapshot();
    }

    public static void loadPlugins() {
        try {
            PluginCore currentPluginCore = PluginCoreDAO.getInstance().loadSnapshot();
            ensurePluginScanRunnable();
            PluginRuntimeStates.reconcileRuntimeStates();
            if (!currentPluginCore.getSetting().getRuntime().getOnDemandEnabled()) {
                pluginScanRunnable.run();
            } else {
                pluginScanRunnable.prepare();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "start plugin exception ", e);
        }
    }

    public static void loadPluginsAsync() {
        ensurePluginScanRunnable();
        if (!PLUGIN_BOOTSTRAP_RUNNING.compareAndSet(false, true)) {
            return;
        }
        PLUGIN_BOOTSTRAP_EXECUTOR.execute(() -> {
            try {
                loadPlugins();
            } finally {
                PLUGIN_BOOTSTRAP_RUNNING.set(false);
            }
        });
    }

    private static void ensurePluginScanRunnable() {
        if (pluginScanRunnable == null) {
            pluginScanRunnable = new PluginScanRunnable();
        }
    }

    public static Map<String, String> getRequiredPlugins() {
        return REQUIRED_PLUGINS;
    }

    public static void loadPlugin(File pluginFile, String pluginId) {
        ensurePluginScanRunnable();
        pluginScanRunnable.loadPlugin(pluginFile, pluginId);
    }

    public static boolean startPluginFileForMetadata(File pluginFile) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0) {
            return false;
        }
        return startPluginFileForMetadata(pluginFile, resolvePluginId(pluginFile));
    }

    static boolean startPluginFileForMetadata(File pluginFile, String pluginId) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0 || StringUtils.isEmpty(pluginId)) {
            return false;
        }
        String pluginShortName = PluginFiles.getPluginShortName(pluginFile);
        if (PluginSessions.isRunningByPluginShortName(pluginShortName) && hasPluginFileChanged(pluginFile, pluginId)) {
            stopPlugin(pluginShortName);
        }
        loadPlugin(pluginFile, pluginId);
        boolean registered = waitForPluginMetadata(pluginShortName, pluginId);
        if (registered) {
            PluginCoreDAO.getInstance().updatePluginFileMd5(pluginShortName, pluginId, PluginFiles.pluginFileMd5(pluginFile));
        }
        return registered;
    }

    private static String resolvePluginId(File pluginFile) {
        String pluginShortName = PluginFiles.getPluginShortName(pluginFile);
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        if (pluginVO != null && pluginVO.getPlugin() != null && !StringUtils.isEmpty(pluginVO.getPlugin().getId())) {
            return pluginVO.getPlugin().getId();
        }
        return UUID.randomUUID().toString();
    }

    private static boolean waitForPluginMetadata(String pluginShortName, String pluginId) {
        long deadline = System.currentTimeMillis() + PLUGIN_METADATA_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            IOSession session = PluginSessions.getLocalSessionByPluginId(pluginId);
            if (session == null) {
                session = PluginSessions.getLocalSessionByPluginShortName(pluginShortName);
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

    public static boolean shouldStartPluginFileForMetadata(File pluginFile, String pluginId) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0 || StringUtils.isEmpty(pluginId)) {
            return false;
        }
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(PluginFiles.getPluginShortName(pluginFile));
        }
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return true;
        }
        if (StringUtils.isEmpty(pluginVO.getFileMd5())) {
            return true;
        }
        return !Objects.equals(pluginVO.getFileMd5(), PluginFiles.pluginFileMd5(pluginFile));
    }

    private static boolean hasPluginFileChanged(File pluginFile, String pluginId) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOById(pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(PluginFiles.getPluginShortName(pluginFile));
        }
        if (pluginVO == null || StringUtils.isEmpty(pluginVO.getFileMd5())) {
            return false;
        }
        return !Objects.equals(pluginVO.getFileMd5(), PluginFiles.pluginFileMd5(pluginFile));
    }

    public static void registerPlugin(IOSession session) {
        PluginSessions.registerPlugin(session, pluginScanRunnable);
    }

    public static void unregisterPluginSession(IOSession session) {
        PluginSessions.unregisterPluginSession(session);
    }

    public static void stopPlugin(String pluginShortName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        if (pluginVO != null && pluginVO.getPlugin() != null) {
            PluginSessions.closeLocalSessionsByPluginId(pluginVO.getPlugin().getId());
        }
        PluginSessions.closeLocalSessionsByPluginShortName(pluginShortName);

        if (pluginScanRunnable != null) {
            pluginScanRunnable.destroy(pluginShortName);
        }
    }

    public static void deletePlugin(String pluginShortName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        PluginSessions.closeLocalSessionsByPluginShortName(pluginShortName);
        if (pluginScanRunnable != null) {
            pluginScanRunnable.destroy(pluginShortName);
        }
        if (pluginVO != null && pluginVO.getPlugin() != null) {
            String pluginId = pluginVO.getPlugin().getId();
            File pluginFile = PluginFiles.getPluginFile(pluginShortName);
            if (pluginFile.exists()) {
                pluginFile.delete();
            }
            PluginRuntimeStates.deletePluginRuntimeReferences(pluginId);
        }
        PluginCoreDAO.getInstance().removePluginByShortName(pluginShortName);
    }

    public static File downloadAndStartPlugin(String fileName) throws Exception {
        File file = PluginFiles.downloadPlugin(fileName);
        if (!startPluginFileForMetadata(file)) {
            throw new RuntimeException("Download succeeded, but plugin metadata was not registered");
        }
        return file;
    }

    public static boolean allRunning() {
        return PluginSessions.allRunning(pluginScanRunnable);
    }
}
