package com.zrlog.plugincore.server.runtime.plugin.bootstrap;

import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginConfig;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.plugin.artifact.PluginFiles;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessionRegistry;
import com.zrlog.plugincore.server.runtime.plugin.log.PluginLogContext;
import com.zrlog.plugincore.server.vo.PluginVO;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginArtifactBootstrapper {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginArtifactBootstrapper.class);
    private static final int MAX_PLUGIN_START_THREADS = 10;

    private final Map<String, String> requiredPlugins;
    private final PluginMetadataBootstrapper metadataBootstrapper;
    private final PluginSessionRegistry sessionRegistry;
    private final PluginConfig pluginConfig;

    public PluginArtifactBootstrapper(Map<String, String> requiredPlugins, PluginMetadataBootstrapper metadataBootstrapper) {
        this(requiredPlugins, metadataBootstrapper, metadataBootstrapper.sessionRegistry(), PluginConfig.unconfigured());
    }

    public PluginArtifactBootstrapper(Map<String, String> requiredPlugins, PluginMetadataBootstrapper metadataBootstrapper,
                                      PluginSessionRegistry sessionRegistry) {
        this(requiredPlugins, metadataBootstrapper, sessionRegistry, PluginConfig.unconfigured());
    }

    public PluginArtifactBootstrapper(Map<String, String> requiredPlugins, PluginMetadataBootstrapper metadataBootstrapper,
                                      PluginSessionRegistry sessionRegistry, PluginConfig pluginConfig) {
        this.requiredPlugins = requiredPlugins;
        this.metadataBootstrapper = metadataBootstrapper;
        this.sessionRegistry = sessionRegistry;
        this.pluginConfig = pluginConfig;
    }

    public void reconcilePluginArtifacts(PluginCore currentPluginCore) {
        bootstrapInstalledPluginArtifacts(currentPluginCore);
        downloadMissingPluginFiles(currentPluginCore);
    }

    public List<String> getAllRunnablePluginIds(PluginCore currentPluginCore) {
        return new ArrayList<>(getAllRunnablePlugin(currentPluginCore).values());
    }

    public Map<String, String> getAllRunnablePlugin(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new HashMap<>();
        if (currentPluginCore != null && currentPluginCore.getPluginInfoMap() != null) {
            currentPluginCore.getPluginInfoMap().values().forEach(pluginVO -> {
                if (pluginVO.getPlugin() == null
                        || sessionRegistry.isRunningByPluginShortName(pluginVO.getPlugin().getShortName())) {
                    return;
                }
                runnablePlugins.put(pluginVO.getPlugin().getShortName(), pluginVO.getPlugin().getId());
            });
        }
        getBootstrapPluginIds(currentPluginCore).forEach((key, value) -> {
            if (runnablePlugins.containsKey(key)) {
                return;
            }
            runnablePlugins.put(key, value);
        });
        return runnablePlugins;
    }

    public int pluginStartThreads(int pluginCount) {
        if (pluginCount <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_PLUGIN_START_THREADS, pluginCount));
    }

    private Map<String, String> getBootstrapPluginIds(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new LinkedHashMap<>();
        requiredPlugins.forEach((pluginShortName, fallbackPluginId) -> {
            if (!sessionRegistry.isRunningByPluginShortName(pluginShortName)) {
                runnablePlugins.put(pluginShortName, pluginIdForInstalledArtifact(currentPluginCore, pluginShortName, fallbackPluginId));
            }
        });
        getInstalledPluginArtifactIds(currentPluginCore).forEach(runnablePlugins::putIfAbsent);
        return runnablePlugins;
    }

    private Map<String, String> getInstalledPluginArtifactIds(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new LinkedHashMap<>();
        for (File file : PluginFiles.pluginFilesIn(new File(pluginConfig().getPluginBasePath()))) {
            String pluginShortName = PluginFiles.getPluginShortName(file);
            if (StringUtils.isEmpty(pluginShortName)) {
                continue;
            }
            if (sessionRegistry.isRunningByPluginShortName(pluginShortName)) {
                continue;
            }
            runnablePlugins.put(pluginShortName, pluginIdForInstalledArtifact(currentPluginCore, pluginShortName));
        }
        return runnablePlugins;
    }

    private void downloadMissingPluginFiles(PluginCore currentPluginCore) {
        boolean download = currentPluginCore == null
                || currentPluginCore.getSetting() == null
                || !currentPluginCore.getSetting().isDisableAutoDownloadLostFile();
        if (!download) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            for (Map.Entry<String, String> pluginEntry : getAllRunnablePlugin(currentPluginCore).entrySet()) {
                String pluginShortName = pluginEntry.getKey();
                String pluginId = pluginEntry.getValue();
                File file = PluginFiles.getPluginFile(pluginShortName);
                if (file.exists() && file.length() > 0) {
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    try (PluginLogContext.Scope ignored = PluginLogContext.open(pluginId, pluginShortName, pluginShortName)) {
                        try {
                            File downloadedFile = PluginFiles.downloadPlugin(file.getName());
                            if (!metadataBootstrapper.startPluginFileForMetadata(downloadedFile, pluginId)) {
                                LOGGER.warning(PluginLogContext.prefix("downloaded plugin " + pluginShortName + " but metadata was not registered"));
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, PluginLogContext.prefix("download error"), e);
                        }
                    }
                }, executorService));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executorService.shutdown();
        }
    }

    private void bootstrapInstalledPluginArtifacts(PluginCore currentPluginCore) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<File> pluginFiles = PluginFiles.pluginFilesIn(new File(pluginConfig().getPluginBasePath()));
        ExecutorService executorService = Executors.newFixedThreadPool(pluginStartThreads(pluginFiles.size()));
        for (File file : pluginFiles) {
            String pluginShortName = PluginFiles.getPluginShortName(file);
            if (StringUtils.isEmpty(pluginShortName)) {
                continue;
            }
            String pluginId = pluginIdForInstalledArtifact(currentPluginCore, pluginShortName);
            if (!metadataBootstrapper.shouldStartPluginFileForMetadata(file, pluginId, currentPluginCore)) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                try (PluginLogContext.Scope ignored = PluginLogContext.open(pluginId, pluginShortName, pluginShortName)) {
                    if (!metadataBootstrapper.startPluginFileForMetadata(file, pluginId)) {
                        LOGGER.warning(PluginLogContext.prefix("plugin " + pluginShortName + " file exists but metadata was not registered"));
                    }
                }
            }, executorService));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executorService.shutdown();
        }
    }

    static String pluginIdForInstalledArtifact(PluginCore pluginCore, String pluginShortName) {
        return pluginIdForInstalledArtifact(pluginCore, pluginShortName, null);
    }

    private static String pluginIdForInstalledArtifact(PluginCore pluginCore, String pluginShortName, String fallbackPluginId) {
        PluginVO pluginVO = pluginVOForInstalledArtifact(pluginCore, pluginShortName);
        if (pluginVO != null && pluginVO.getPlugin() != null
                && !StringUtils.isEmpty(pluginVO.getPlugin().getId())) {
            return pluginVO.getPlugin().getId();
        }
        if (!StringUtils.isEmpty(fallbackPluginId)) {
            return fallbackPluginId;
        }
        return UUID.randomUUID().toString();
    }

    private static PluginVO pluginVOForInstalledArtifact(PluginCore pluginCore, String pluginShortName) {
        if (pluginCore == null || pluginCore.getPluginInfoMap() == null || StringUtils.isEmpty(pluginShortName)) {
            return null;
        }
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

    private PluginConfig pluginConfig() {
        return pluginConfig;
    }
}
