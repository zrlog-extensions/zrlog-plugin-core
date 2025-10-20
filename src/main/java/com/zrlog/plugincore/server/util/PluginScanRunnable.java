package com.zrlog.plugincore.server.util;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.type.PluginStatus;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginScanRunnable extends BaseLockObject implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginUtil.class);

    private final PluginCore pluginCore;
    private final Map<String, Process> processMap = new ConcurrentHashMap<>();

    private void registerHook() {
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread(() -> {
            for (Map.Entry<String, Process> entry : processMap.entrySet()) {
                entry.getValue().destroy();
                LOGGER.info("close plugin " + " " + entry.getKey());
            }
        }));
    }

    public PluginScanRunnable(PluginCore pluginCore) {
        this.pluginCore = pluginCore;
        registerHook();
    }


    private void printInputStreamWithThread(final Process pr, final InputStream in, final String pluginName,
                                            final String printLevel, final String uuid) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String str = br.readLine();
                if (Objects.isNull(str)) {
                    return;
                }
                if ("PERROR".equals(printLevel) && str.startsWith("Error: Invalid or corrupt jarfile")) {
                    processMap.remove(uuid);
                } else {
                    while ((str = br.readLine()) != null) {
                        System.out.println("[" + printLevel + "]" + ": " + pluginName + " - " + str);
                    }
                }
            } catch (IOException e) {
                if (EnvKit.isDevMode()) {
                    LOGGER.log(Level.SEVERE, "plugin output error", e);
                }
            } finally {
                try {
                    destroy(pluginName);
                } finally {
                    pr.destroy();
                }
            }
        }).start();
    }

    public void destroy(String pluginName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByName(pluginName);
        if (pluginVO != null) {
            String pluginId = pluginVO.getPlugin().getId();
            IOSession session = PluginConfig.getInstance().getSessionMap().get(pluginId);
            if (session != null) {
                session.close();
            }
            //关闭进程
            Process process = processMap.get(pluginId);
            if (process != null) {
                process.destroy();
            }
            processMap.remove(pluginId);
            //移除相关映射
            PluginConfig.getInstance().getSessionMap().remove(pluginId);
        }
    }

    private String getProgramName(File file) {
        if (file.getName().endsWith(".jar")) {
            if (Objects.isNull(System.getProperty("java.home"))) {
                return "java";
            }
            return System.getProperty("java.home") + "/bin/java";
        }
        return file.toString();
    }

    public void loadPlugin(final File pluginFile, String pluginId) {
        if (pluginFile == null || !pluginFile.exists()) {
            return;
        }
        lock.lock();
        try {
            if (processMap.containsKey(pluginId)) {
                return;
            }
            String pluginName = PluginUtil.getPluginName(pluginFile);
            LOGGER.info("run plugin " + pluginName);
            String userDir = PluginConfig.getInstance().getPluginHomeFolder(pluginName);
            String tmpDir = PluginConfig.getInstance().getPluginTempFolder(pluginName);
            new File(userDir).mkdirs();
            new File(tmpDir).mkdirs();
            List<String> args = new ArrayList<>();
            if (pluginFile.getName().endsWith(".jar")) {
                args.add("-Djava.io.tmpdir=" + tmpDir);
                args.add("-Duser.dir=" + userDir);
                args.add("-Duser.home=" + userDir);
                args.add(ConfigKit.get("pluginJvmArgs", "") + "");
                args.add("-jar");
                args.add(pluginFile.toString());
                args.add(PluginConfig.getInstance().getMasterPort() + "");
                args.add(pluginId);
            } else {
                if (File.separatorChar == '/') {
                    CmdUtil.sendCmd("chmod", "a+x", pluginFile.toString());
                }
                args.add(PluginConfig.getInstance().getMasterPort() + "");
                args.add(pluginId);
                args.add("-Djava.io.tmpdir=" + tmpDir);
                args.add("-Duser.dir=" + userDir);
                args.add("-Duser.home=" + userDir);
            }
            Process pr = CmdUtil.getProcess(getProgramName(pluginFile), args.toArray(new Object[0]));
            if (pr != null) {
                processMap.put(pluginId, pr);
                printInputStreamWithThread(pr, pr.getInputStream(), pluginName, "PINFO", pluginId);
                printInputStreamWithThread(pr, pr.getErrorStream(), pluginName, "PERROR", pluginId);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        checkLostFile();
        Set<Map.Entry<String, String>> entries = getAllRunnablePlugin().entrySet();
        for (Map.Entry<String, String> pluginVO : entries) {
            File file = PluginUtil.getPluginFile(pluginVO.getKey());
            if (!file.getName().endsWith(".jar") && !file.getName().endsWith(".bin") && !file.getName().endsWith(".exe")) {
                continue;
            }
            loadPlugin(file, pluginVO.getValue());
        }
    }

    public List<String> getAllRunnablePluginIds() {
        if (EnvKit.isFaaSMode()) {
            return new ArrayList<>(getDownloadedPluginList().values());
        }
        return new ArrayList<>(getAllRunnablePlugin().values());
    }

    private Map<String, String> getAllRunnablePlugin() {
        Map<String, String> runnablePlugins = new HashMap<>();
        if (pluginCore != null && pluginCore.getPluginInfoMap() != null) {
            pluginCore.getPluginInfoMap().values().forEach(pluginVO -> {
                if (PluginUtil.isRunningByPluginId(pluginVO.getPlugin().getId())) {
                    return;
                }
                runnablePlugins.put(pluginVO.getPlugin().getShortName(), pluginVO.getPlugin().getId());
            });
        }
        getDownloadedPluginList().forEach((key, value) -> {
            if (runnablePlugins.containsKey(key)) {
                return;
            }
            runnablePlugins.put(key, value);
        });
        return runnablePlugins;
    }

    private Map<String, String> getDownloadedPluginList() {
        if (!EnvKit.isFaaSMode()) {
            return PluginUtil.getRequiredPlugins();
        }
        //FaaS 模式下，存在的插件即为需要运行
        Map<String, String> runnablePlugins = new HashMap<>();
        //System.out.println("PluginConfig.getInstance().getPluginBasePath() = " + PluginConfig.getInstance().getPluginBasePath());
        File[] files = new File(PluginConfig.getInstance().getPluginBasePath()).listFiles();
        //System.out.println("files = " + Arrays.toString(files));
        for (File s : Objects.requireNonNull(files)) {
            if (s.isDirectory()) {
                continue;
            }
            String pluginName = PluginUtil.getPluginName(s);
            if (StringUtils.isEmpty(pluginName)) {
                continue;
            }
            Optional<PluginVO> pluginVOOptional = pluginCore.getPluginInfoMap().values().stream().filter(e -> e.getPlugin().getShortName().equals(pluginName)).findFirst();
            if (pluginVOOptional.isPresent() && Objects.nonNull(pluginVOOptional.get().getPlugin())) {
                runnablePlugins.put(pluginName, pluginVOOptional.get().getPlugin().getId());
            } else {
                runnablePlugins.put(pluginName, UUID.randomUUID().toString());
            }
        }
        return runnablePlugins;
    }


    private void checkLostFile() {
        //FaaS 环境要求快速启动，不在运行期间去下载
        if (EnvKit.isFaaSMode()) {
            return;
        }
        boolean download = !pluginCore.getSetting().isDisableAutoDownloadLostFile();
        if (!download) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            for (String pluginShortName : getAllRunnablePlugin().keySet()) {
                File file = PluginUtil.getPluginFile(pluginShortName);
                if (file.exists() && file.length() > 0) {
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        PluginUtil.downloadPlugin(file.getName());
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
}
