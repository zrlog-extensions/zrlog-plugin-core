package com.zrlog.plugincore.server.runtime.plugin.bootstrap;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.plugin.lifecycle.PluginLifecycleService;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessions;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginBootstrapService {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginBootstrapService.class);

    private final Map<String, String> requiredPlugins;
    private final Object bootstrapLock = new Object();
    private final AtomicBoolean bootstrapRunning = new AtomicBoolean(false);
    private final ExecutorService bootstrapExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "zrlog-plugin-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private volatile CompletableFuture<Void> bootstrapFuture = CompletableFuture.completedFuture(null);
    private final PluginMetadataBootstrapper metadataBootstrapper;
    private final PluginStartupCoordinator pluginStartupCoordinator;
    private final PluginLifecycleService lifecycleService;

    public PluginBootstrapService(Map<String, String> requiredPlugins,
                                  PluginStartupCoordinator pluginStartupCoordinator,
                                  PluginMetadataBootstrapper metadataBootstrapper,
                                  PluginLifecycleService lifecycleService) {
        this.requiredPlugins = requiredPlugins;
        this.pluginStartupCoordinator = pluginStartupCoordinator;
        this.metadataBootstrapper = metadataBootstrapper;
        this.lifecycleService = lifecycleService;
    }

    public void verifyPluginCoreReadable() {
        PluginCoreDAO.getInstance().loadSnapshot();
    }

    public void loadPlugins() {
        try {
            PluginCore currentPluginCore = PluginCoreDAO.getInstance().loadSnapshot();
            PluginRuntimeStates.reconcileRuntimeStates();
            if (!currentPluginCore.getSetting().getRuntime().getOnDemandEnabled()) {
                pluginStartupCoordinator.startRunnablePlugins();
            } else {
                pluginStartupCoordinator.prepare();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "start plugin exception ", e);
        }
    }

    public void loadPluginsAsync() {
        CompletableFuture<Void> currentFuture;
        synchronized (bootstrapLock) {
            if (bootstrapRunning.get()) {
                return;
            }
            currentFuture = new CompletableFuture<>();
            bootstrapFuture = currentFuture;
            bootstrapRunning.set(true);
        }
        bootstrapExecutor.execute(() -> {
            try {
                loadPlugins();
                bootstrapRunning.set(false);
                currentFuture.complete(null);
            } catch (RuntimeException e) {
                bootstrapRunning.set(false);
                currentFuture.completeExceptionally(e);
                throw e;
            } catch (Error e) {
                bootstrapRunning.set(false);
                currentFuture.completeExceptionally(e);
                throw e;
            }
        });
    }

    public boolean awaitCurrentBootstrap() {
        CompletableFuture<Void> currentFuture = bootstrapFuture;
        if (currentFuture == null || currentFuture.isDone()) {
            return true;
        }
        try {
            currentFuture.get();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            LOGGER.log(Level.WARNING, "plugin metadata bootstrap failed", e);
            return false;
        }
    }

    public boolean isBootstrapRunning() {
        return bootstrapRunning.get();
    }

    public boolean isCurrentBootstrapReady() {
        CompletableFuture<Void> currentFuture = bootstrapFuture;
        return currentFuture != null
                && currentFuture.isDone()
                && !currentFuture.isCancelled()
                && !currentFuture.isCompletedExceptionally();
    }

    public Map<String, String> getRequiredPlugins() {
        return requiredPlugins;
    }

    public void loadPlugin(File pluginFile, String pluginId) {
        pluginStartupCoordinator.loadPlugin(pluginFile, pluginId);
    }

    public boolean startPluginFileForMetadata(File pluginFile) {
        return metadataBootstrapper.startPluginFileForMetadata(pluginFile);
    }

    boolean startPluginFileForMetadata(File pluginFile, String pluginId) {
        return metadataBootstrapper.startPluginFileForMetadata(pluginFile, pluginId);
    }

    public boolean shouldStartPluginFileForMetadata(File pluginFile, String pluginId) {
        return metadataBootstrapper.shouldStartPluginFileForMetadata(pluginFile, pluginId);
    }

    boolean shouldStartPluginFileForMetadata(File pluginFile, String pluginId, PluginCore pluginCore) {
        return metadataBootstrapper.shouldStartPluginFileForMetadata(pluginFile, pluginId, pluginCore);
    }

    public void registerPlugin(IOSession session) {
        lifecycleService.registerPlugin(session);
    }

    public void unregisterPluginSession(IOSession session) {
        lifecycleService.unregisterPluginSession(session);
    }

    public void stopPlugin(String pluginShortName) {
        lifecycleService.stopPlugin(pluginShortName);
    }

    public void deletePlugin(String pluginShortName) {
        lifecycleService.deletePlugin(pluginShortName);
    }

    public File downloadAndStartPlugin(String fileName) throws Exception {
        return metadataBootstrapper.downloadAndStartPlugin(fileName);
    }

    public boolean allRunning() {
        return pluginStartupCoordinator.getAllRunnablePluginIds().stream().allMatch(PluginSessions::isRunningByPluginId);
    }
}
