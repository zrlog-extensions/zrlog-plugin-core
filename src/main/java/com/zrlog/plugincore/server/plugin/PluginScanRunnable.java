package com.zrlog.plugincore.server.plugin;
import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;
import com.zrlog.plugincore.server.util.CmdUtil;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginScanRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginScanRunnable.class);
    private static final long PROCESS_START_GRACE_MS = 30000L;
    private static final int MAX_PLUGIN_START_THREADS = 10;

    private final Map<String, Process> processMap = new ConcurrentHashMap<>();
    private final Map<String, String> processPluginShortNameMap = new ConcurrentHashMap<>();
    private final Map<String, Long> processStartedAtMap = new ConcurrentHashMap<>();
    private final Map<String, Long> processIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> processRuntimeInstanceIdMap = new ConcurrentHashMap<>();
    private final Map<String, Object> pluginStartLocks = new ConcurrentHashMap<>();

    private void registerHook() {
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread(() -> {
            for (Map.Entry<String, Process> entry : processMap.entrySet()) {
                entry.getValue().destroy();
                LOGGER.info("close plugin " + " " + entry.getKey());
            }
        }));
    }

    public PluginScanRunnable() {
        registerHook();
    }


    private void printInputStreamWithThread(final Process pr, final InputStream in, final String pluginShortName,
                                            final String printLevel, final String uuid,
                                            AtomicBoolean cleaned) {
        new Thread(() -> {
            try {
                drainProcessOutput(pr, in, pluginShortName, printLevel, uuid, cleaned);
            } catch (IOException e) {
                if (EnvKit.isDevMode()) {
                    LOGGER.log(Level.SEVERE, "plugin output error", e);
                }
            }
        }, "zrlog-plugin-output-" + printLevel + "-" + pluginShortName).start();
    }

    void drainProcessOutput(Process pr, InputStream in, String pluginShortName, String printLevel, String uuid,
                            AtomicBoolean cleaned) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String str;
            while ((str = br.readLine()) != null) {
                if ("PERROR".equals(printLevel) && str.startsWith("Error: Invalid or corrupt jarfile")) {
                    pr.destroy();
                    cleanupExitedProcess(pr, pluginShortName, uuid, cleaned);
                    return;
                }
                System.out.println("[" + printLevel + "]" + ": " + pluginShortName + " - " + str);
            }
        }
    }

    private void watchProcessExit(final Process pr, final String pluginShortName, final String pluginId,
                                  AtomicBoolean cleaned) {
        new Thread(() -> {
            try {
                int exitCode = pr.waitFor();
                if (EnvKit.isDevMode()) {
                    LOGGER.info("plugin " + pluginShortName + " exited with code " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cleanupExitedProcess(pr, pluginShortName, pluginId, cleaned);
            }
        }, "zrlog-plugin-watch-" + pluginShortName).start();
    }

    private void cleanupExitedProcess(Process process, String pluginShortName, String pluginId, AtomicBoolean cleaned) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        if (!processMap.remove(pluginId, process)) {
            return;
        }
        PluginSessions.closeLocalSessionsByPluginId(pluginId);
        processPluginShortNameMap.remove(pluginId);
        processStartedAtMap.remove(pluginId);
        String pluginName = pluginNameOrShortName(pluginId, pluginShortName);
        markProcessRuntimeStopped(pluginId, pluginName);
        runtimeStateService().markStopped(pluginId, pluginName);
    }

    public void destroy(String pluginShortName) {
        PluginSessions.closeLocalSessionsByPluginShortName(pluginShortName);
        boolean destroyed = false;
        for (Map.Entry<String, String> entry : new ArrayList<Map.Entry<String, String>>(processPluginShortNameMap.entrySet())) {
            if (!Objects.equals(pluginShortName, entry.getValue())) {
                continue;
            }
            destroyByPluginId(entry.getKey(), pluginShortName);
            destroyed = true;
        }
        if (destroyed) {
            return;
        }
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        if (pluginVO != null && pluginVO.getPlugin() != null) {
            destroyByPluginId(pluginVO.getPlugin().getId(), pluginShortName);
        }
    }

    public void destroyByPluginId(String pluginId, String pluginShortName) {
        PluginSessions.closeLocalSessionsByPluginId(pluginId);
        Process process = processMap.remove(pluginId);
        if (process != null) {
            process.destroy();
        }
        processPluginShortNameMap.remove(pluginId);
        processStartedAtMap.remove(pluginId);
        String pluginName = pluginNameOrShortName(pluginId, pluginShortName);
        markProcessRuntimeStopped(pluginId, pluginName);
        runtimeStateService().markStopped(pluginId, pluginName);
    }

    public void loadPlugin(final File pluginFile, String pluginId) {
        if (pluginFile == null || !pluginFile.exists()) {
            return;
        }
        String pluginShortName = PluginFiles.getPluginShortName(pluginFile);
        synchronized (pluginStartLock(pluginShortName, pluginId)) {
            destroyOtherProcesses(pluginShortName, pluginId);
            if (!prepareProcessSlot(pluginId, pluginShortName)) {
                return;
            }
            PluginRuntimeStates.removeLocalRuntimeInstances(pluginId);
            LOGGER.info("run plugin " + pluginShortName);
            String userDir = PluginConfig.getInstance().getPluginHomeFolder(pluginShortName);
            String tmpDir = PluginConfig.getInstance().getPluginTempFolder(pluginShortName);
            new File(userDir).mkdirs();
            new File(tmpDir).mkdirs();
            LaunchCommand launchCommand = buildLaunchCommand(
                    pluginFile,
                    PluginConfig.getInstance().getMasterPort(),
                    pluginId,
                    userDir,
                    tmpDir,
                    ConfigKit.get("pluginJvmArgs", "") + "",
                    System.getProperty("java.home")
            );
            if (!pluginFile.getName().endsWith(".jar")) {
                if (File.separatorChar == '/') {
                    CmdUtil.sendCmd("chmod", "a+x", pluginFile.toString());
                }
            }
            Process pr = CmdUtil.getProcess(
                    launchCommand.workingDirectory,
                    launchCommand.environment,
                    launchCommand.program,
                    launchCommand.args.toArray(new Object[0])
            );
            if (pr != null) {
                long processId = pr.pid();
                String runtimeInstanceId = PluginRuntimeStates.newRuntimeInstanceId(processId);
                processMap.put(pluginId, pr);
                processPluginShortNameMap.put(pluginId, pluginShortName);
                processStartedAtMap.put(pluginId, System.currentTimeMillis());
                processIdMap.put(pluginId, processId);
                processRuntimeInstanceIdMap.put(pluginId, runtimeInstanceId);
                runtimeStateService(runtimeInstanceId).markStarting(pluginId,
                        pluginNameOrShortName(pluginId, pluginShortName), runtimeMode(pluginFile), processId);
                AtomicBoolean cleaned = new AtomicBoolean(false);
                printInputStreamWithThread(pr, pr.getInputStream(), pluginShortName, "PINFO", pluginId, cleaned);
                printInputStreamWithThread(pr, pr.getErrorStream(), pluginShortName, "PERROR", pluginId, cleaned);
                watchProcessExit(pr, pluginShortName, pluginId, cleaned);
                return;
            }
            runtimeStateService().markFailed(pluginId, pluginNameOrShortName(pluginId, pluginShortName), "Plugin process start failed");
        }
    }

    private Object pluginStartLock(String pluginShortName, String pluginId) {
        String key = StringUtils.isEmpty(pluginShortName) ? pluginId : pluginShortName;
        if (StringUtils.isEmpty(key)) {
            key = "_unknown";
        }
        return pluginStartLocks.computeIfAbsent(key, ignored -> new Object());
    }

    static LaunchCommand buildLaunchCommand(File pluginFile,
                                            int masterPort,
                                            String pluginId,
                                            String userDir,
                                            String tmpDir,
                                            String pluginJvmArgs,
                                            String javaHome) {
        List<String> args = new ArrayList<>();
        if (pluginFile.getName().endsWith(".jar")) {
            args.add("-Djava.io.tmpdir=" + tmpDir);
            args.add("-Duser.dir=" + userDir);
            args.add("-Duser.home=" + userDir);
            appendJvmArgs(args, pluginJvmArgs);
            args.add("-jar");
            args.add(pluginFile.getAbsolutePath());
        }
        args.add(masterPort + "");
        args.add(pluginId);
        Map<String, String> environment = new HashMap<>();
        environment.put("HOME", userDir);
        environment.put("USERPROFILE", userDir);
        environment.put("TMPDIR", tmpDir);
        environment.put("TEMP", tmpDir);
        environment.put("TMP", tmpDir);
        return new LaunchCommand(programName(pluginFile, javaHome), args, new File(userDir), environment);
    }

    private static void appendJvmArgs(List<String> args, String pluginJvmArgs) {
        if (StringUtils.isEmpty(pluginJvmArgs)) {
            return;
        }
        for (String arg : pluginJvmArgs.trim().split("\\s+")) {
            if (!StringUtils.isEmpty(arg)) {
                args.add(arg);
            }
        }
    }

    public static String runtimeMode(File pluginFile) {
        if (pluginFile != null && pluginFile.getName().endsWith(".jar")) {
            return "process";
        }
        return "native";
    }

    private static String programName(File file, String javaHome) {
        if (file.getName().endsWith(".jar")) {
            if (Objects.isNull(javaHome)) {
                return "java";
            }
            return javaHome + "/bin/java";
        }
        return file.getAbsolutePath();
    }

    static class LaunchCommand {

        final String program;
        final List<String> args;
        final File workingDirectory;
        final Map<String, String> environment;

        LaunchCommand(String program, List<String> args, File workingDirectory, Map<String, String> environment) {
            this.program = program;
            this.args = args;
            this.workingDirectory = workingDirectory;
            this.environment = environment;
        }
    }

    private void destroyOtherProcesses(String pluginShortName, String currentPluginId) {
        for (Map.Entry<String, String> entry : new ArrayList<Map.Entry<String, String>>(processPluginShortNameMap.entrySet())) {
            if (Objects.equals(currentPluginId, entry.getKey()) || !Objects.equals(pluginShortName, entry.getValue())) {
                continue;
            }
            destroyByPluginId(entry.getKey(), pluginShortName);
        }
    }

    private boolean prepareProcessSlot(String pluginId, String pluginShortName) {
        Process existingProcess = processMap.get(pluginId);
        if (existingProcess == null) {
            return true;
        }
        if (PluginSessions.getLocalSessionByPluginId(pluginId) != null) {
            return false;
        }
        if (existingProcess.isAlive() && withinStartGrace(pluginId)) {
            return false;
        }
        if (processMap.remove(pluginId, existingProcess)) {
            existingProcess.destroy();
            processPluginShortNameMap.remove(pluginId);
            processStartedAtMap.remove(pluginId);
            markProcessRuntimeStopped(pluginId, pluginNameOrShortName(pluginId, pluginShortName));
            PluginSessions.closeLocalSessionsByPluginId(pluginId);
            LOGGER.warning("restart plugin " + pluginShortName + " because process has no local session");
        }
        return true;
    }

    private boolean withinStartGrace(String pluginId) {
        Long startedAt = processStartedAtMap.get(pluginId);
        return startedAt != null && System.currentTimeMillis() - startedAt < PROCESS_START_GRACE_MS;
    }

    private PluginRuntimeStateService runtimeStateService() {
        return new PluginRuntimeStateService(new PluginRuntimeStateStore(new WebsiteRuntimeKvStore()), new DefaultPluginRuntimeStarter());
    }

    private PluginRuntimeStateService runtimeStateService(String runtimeInstanceId) {
        return new PluginRuntimeStateService(new PluginRuntimeStateStore(new WebsiteRuntimeKvStore()),
                new DefaultPluginRuntimeStarter(), runtimeInstanceId);
    }

    public Long processIdByPluginId(String pluginId) {
        return processIdMap.get(pluginId);
    }

    public Optional<String> runtimeInstanceIdByPluginId(String pluginId) {
        return Optional.ofNullable(processRuntimeInstanceIdMap.get(pluginId));
    }

    private void markProcessRuntimeStopped(String pluginId, String pluginName) {
        Long processId = processIdMap.remove(pluginId);
        String runtimeInstanceId = processRuntimeInstanceIdMap.remove(pluginId);
        if (processId == null || runtimeInstanceId == null) {
            return;
        }
        runtimeStateService(runtimeInstanceId).markStopped(pluginId, pluginName);
    }

    private String pluginNameOrShortName(String pluginId, String fallback) {
        for (PluginVO pluginVO : currentPluginCore().getPluginInfoMap().values()) {
            if (pluginVO.getPlugin() != null && Objects.equals(pluginId, pluginVO.getPlugin().getId())) {
                return PluginSessions.nameOrShortName(pluginVO.getPlugin());
            }
        }
        return fallback;
    }

    @Override
    public void run() {
        PluginCore currentPluginCore = currentPluginCore();
        reconcilePluginArtifacts(currentPluginCore);
        Set<Map.Entry<String, String>> entries = getAllRunnablePlugin(currentPluginCore).entrySet();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(pluginStartThreads(entries.size()));
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
                    loadPlugin(file, pluginId);
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

    private int pluginStartThreads(int pluginCount) {
        if (pluginCount <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_PLUGIN_START_THREADS, pluginCount));
    }

    public void prepare() {
        reconcilePluginArtifacts(currentPluginCore());
    }

    public List<String> getAllRunnablePluginIds() {
        return new ArrayList<>(getAllRunnablePlugin(currentPluginCore()).values());
    }

    private Map<String, String> getAllRunnablePlugin(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new HashMap<>();
        if (currentPluginCore != null && currentPluginCore.getPluginInfoMap() != null) {
            currentPluginCore.getPluginInfoMap().values().forEach(pluginVO -> {
                if (pluginVO.getPlugin() == null || PluginSessions.isRunningByPluginShortName(pluginVO.getPlugin().getShortName())) {
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

    private Map<String, String> getBootstrapPluginIds(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new LinkedHashMap<>();
        PluginBootstrap.getRequiredPlugins().forEach((pluginShortName, fallbackPluginId) -> {
            if (!PluginSessions.isRunningByPluginShortName(pluginShortName)) {
                runnablePlugins.put(pluginShortName, pluginIdForInstalledArtifact(currentPluginCore, pluginShortName, fallbackPluginId));
            }
        });
        getInstalledPluginArtifactIds(currentPluginCore).forEach(runnablePlugins::putIfAbsent);
        return runnablePlugins;
    }

    private Map<String, String> getInstalledPluginArtifactIds(PluginCore currentPluginCore) {
        Map<String, String> runnablePlugins = new LinkedHashMap<>();
        for (File file : PluginFiles.pluginFilesIn(new File(PluginConfig.getInstance().getPluginBasePath()))) {
            String pluginShortName = PluginFiles.getPluginShortName(file);
            if (StringUtils.isEmpty(pluginShortName)) {
                continue;
            }
            if (PluginSessions.isRunningByPluginShortName(pluginShortName)) {
                continue;
            }
            runnablePlugins.put(pluginShortName, pluginIdForInstalledArtifact(currentPluginCore, pluginShortName));
        }
        return runnablePlugins;
    }


    private void reconcilePluginArtifacts(PluginCore currentPluginCore) {
        bootstrapInstalledPluginArtifacts(currentPluginCore);
        downloadMissingPluginFiles(currentPluginCore);
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
                    try {
                        File downloadedFile = PluginFiles.downloadPlugin(file.getName());
                        if (!PluginBootstrap.startPluginFileForMetadata(downloadedFile, pluginId)) {
                            LOGGER.warning("downloaded plugin " + pluginShortName + " but metadata was not registered");
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "download error", e);
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
        List<File> pluginFiles = PluginFiles.pluginFilesIn(new File(PluginConfig.getInstance().getPluginBasePath()));
        ExecutorService executorService = Executors.newFixedThreadPool(pluginStartThreads(pluginFiles.size()));
        for (File file : pluginFiles) {
            String pluginShortName = PluginFiles.getPluginShortName(file);
            if (StringUtils.isEmpty(pluginShortName)) {
                continue;
            }
            String pluginId = pluginIdForInstalledArtifact(currentPluginCore, pluginShortName);
            // installed-plugins 是可信 artifact 清单，但是否需要重新收集元数据由文件指纹决定。
            if (!PluginBootstrap.shouldStartPluginFileForMetadata(file, pluginId, currentPluginCore)) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                if (!PluginBootstrap.startPluginFileForMetadata(file, pluginId)) {
                    LOGGER.warning("plugin " + pluginShortName + " file exists but metadata was not registered");
                }
            }, executorService));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executorService.shutdown();
        }
    }

    private PluginCore currentPluginCore() {
        return PluginCoreDAO.getInstance().loadSnapshot();
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
}
