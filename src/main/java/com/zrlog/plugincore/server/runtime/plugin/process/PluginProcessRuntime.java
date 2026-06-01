package com.zrlog.plugincore.server.runtime.plugin.process;

import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.runtime.PluginRuntimeContexts;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginConfig;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.plugin.artifact.PluginFiles;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessionRegistry;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;
import com.zrlog.plugincore.server.util.CmdUtil;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginProcessRuntime {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginProcessRuntime.class);
    private static final long PROCESS_START_GRACE_MS = 30000L;

    private final PluginSessionRegistry sessionRegistry;
    private final Map<String, Process> processMap = new ConcurrentHashMap<>();
    private final Map<String, String> processPluginShortNameMap = new ConcurrentHashMap<>();
    private final Map<String, Long> processStartedAtMap = new ConcurrentHashMap<>();
    private final Map<String, Long> processIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> processRuntimeInstanceIdMap = new ConcurrentHashMap<>();
    private final Map<String, Object> pluginStartLocks = new ConcurrentHashMap<>();
    private final PluginConfig pluginConfig;

    public PluginProcessRuntime() {
        this(new PluginSessionRegistry(), PluginRuntimeContexts.current().pluginConfig());
    }

    public PluginProcessRuntime(PluginSessionRegistry sessionRegistry) {
        this(sessionRegistry, PluginRuntimeContexts.current().pluginConfig());
    }

    public PluginProcessRuntime(PluginSessionRegistry sessionRegistry, PluginConfig pluginConfig) {
        this.sessionRegistry = sessionRegistry;
        this.pluginConfig = pluginConfig;
        registerHook();
    }

    private void registerHook() {
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread(() -> {
            for (Map.Entry<String, Process> entry : processMap.entrySet()) {
                entry.getValue().destroy();
                LOGGER.info("close plugin " + " " + entry.getKey());
            }
        }));
    }

    public void destroy(String pluginShortName) {
        sessionRegistry.closeLocalSessionsByPluginShortName(pluginShortName);
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
        sessionRegistry.closeLocalSessionsByPluginId(pluginId);
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
            String userDir = pluginConfig.getPluginHomeFolder(pluginShortName);
            String tmpDir = pluginConfig.getPluginTempFolder(pluginShortName);
            new File(userDir).mkdirs();
            new File(tmpDir).mkdirs();
            LaunchCommand launchCommand = buildLaunchCommand(
                    pluginFile,
                    pluginConfig.getMasterPort(),
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
        sessionRegistry.closeLocalSessionsByPluginId(pluginId);
        processPluginShortNameMap.remove(pluginId);
        processStartedAtMap.remove(pluginId);
        String pluginName = pluginNameOrShortName(pluginId, pluginShortName);
        markProcessRuntimeStopped(pluginId, pluginName);
        runtimeStateService().markStopped(pluginId, pluginName);
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
        if (sessionRegistry.getLocalSessionByPluginId(pluginId) != null) {
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
            sessionRegistry.closeLocalSessionsByPluginId(pluginId);
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

    public PluginSessionRegistry sessionRegistry() {
        return sessionRegistry;
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
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        for (PluginVO pluginVO : pluginCore.getPluginInfoMap().values()) {
            if (pluginVO.getPlugin() != null && Objects.equals(pluginId, pluginVO.getPlugin().getId())) {
                return sessionRegistry.nameOrShortName(pluginVO.getPlugin());
            }
        }
        return fallback;
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
}
