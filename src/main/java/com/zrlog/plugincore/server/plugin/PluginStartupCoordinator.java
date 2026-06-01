package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginStartupCoordinator {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginStartupCoordinator.class);

    private final PluginProcessRuntime processRuntime;
    private final PluginArtifactBootstrapper artifactBootstrapper;

    public PluginStartupCoordinator(PluginProcessRuntime processRuntime, PluginArtifactBootstrapper artifactBootstrapper) {
        this.processRuntime = processRuntime;
        this.artifactBootstrapper = artifactBootstrapper;
    }

    PluginProcessRuntime processRuntime() {
        return processRuntime;
    }

    public void destroy(String pluginShortName) {
        processRuntime.destroy(pluginShortName);
    }

    public void destroyByPluginId(String pluginId, String pluginShortName) {
        processRuntime.destroyByPluginId(pluginId, pluginShortName);
    }

    public void loadPlugin(final File pluginFile, String pluginId) {
        processRuntime.loadPlugin(pluginFile, pluginId);
    }

    public void startRunnablePlugins() {
        PluginCore currentPluginCore = currentPluginCore();
        artifactBootstrapper.reconcilePluginArtifacts(currentPluginCore);
        Set<Map.Entry<String, String>> entries = artifactBootstrapper.getAllRunnablePlugin(currentPluginCore).entrySet();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(artifactBootstrapper.pluginStartThreads(entries.size()));
        for (Map.Entry<String, String> pluginVO : entries) {
            File file = PluginFiles.getAvailablePluginFile(pluginVO.getKey());
            if (!file.getName().endsWith(".jar") && !file.getName().endsWith(".bin") && !file.getName().endsWith(".exe")) {
                continue;
            }
            if (!file.exists() || file.length() == 0) {
                continue;
            }
            String pluginId = pluginVO.getValue();
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    processRuntime.loadPlugin(file, pluginId);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.SEVERE, "start plugin " + file.getName() + " error", e);
                }
            }, executorService));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executorService.shutdown();
        }
    }

    public void prepare() {
        artifactBootstrapper.reconcilePluginArtifacts(currentPluginCore());
    }

    public List<String> getAllRunnablePluginIds() {
        return artifactBootstrapper.getAllRunnablePluginIds(currentPluginCore());
    }

    private PluginCore currentPluginCore() {
        return PluginCoreDAO.getInstance().loadSnapshot();
    }
}
