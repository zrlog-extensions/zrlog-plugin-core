package com.zrlog.plugincore.server.plugin;

import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.SecurityUtils;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.util.HttpUtils;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginFiles {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginFiles.class);

    private PluginFiles() {
    }

    public static String getPluginShortName(File file) {
        return file.getName()
                .replace("-Darwin", "")
                .replace("-x86_64", "")
                .replace("-Linux", "")
                .replace("-Windows", "")
                .replace("-arm64", "")
                .replace("-amd64", "")
                .replace(".bin", "")
                .replace(".exe", "")
                .replace(".jar", "");
    }

    static List<File> pluginFilesIn(File pluginBasePath) {
        if (pluginBasePath == null || !pluginBasePath.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = pluginBasePath.listFiles(file -> file.isFile() && file.length() > 0 && isPluginFile(file));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> pluginFiles = new ArrayList<>(Arrays.asList(files));
        pluginFiles.sort(Comparator.comparing(File::getName));
        return pluginFiles;
    }

    private static boolean isPluginFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        return name.endsWith(".jar") || name.endsWith(".bin") || name.endsWith(".exe");
    }

    static String pluginFileMd5(File pluginFile) {
        if (pluginFile == null || !pluginFile.exists() || pluginFile.length() == 0) {
            return "";
        }
        try {
            String md5 = SecurityUtils.md5ByFile(pluginFile);
            return md5 == null ? "" : md5;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "calculate plugin md5 error", e);
            return "";
        }
    }

    static void copyInputStreamToFile(InputStream inputStream, String filePath) {
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            LOGGER.info("copy plugin error " + e.getMessage());
        } finally {
            if (Objects.nonNull(inputStream)) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static File getPluginFile(String pluginShortName) {
        String filename = StringUtils.isEmpty(Application.NATIVE_INFO) ? pluginShortName + ".jar" :
                pluginShortName + "-" + Application.NATIVE_INFO + (Application.NATIVE_INFO.contains("Window") ? ".exe" : ".bin");
        return new File(PluginConfig.getInstance().getPluginBasePath() + "/" + filename);
    }

    public static File getAvailablePluginFile(String pluginShortName) {
        File configuredFile = getPluginFile(pluginShortName);
        if (configuredFile.exists() && configuredFile.length() > 0) {
            return configuredFile;
        }
        File downloadedFile = downloadPluginFile(configuredFile.getName());
        if (downloadedFile.exists() && downloadedFile.length() > 0) {
            return downloadedFile;
        }
        return configuredFile;
    }

    public static File ensurePluginFile(String pluginShortName) {
        File file = getAvailablePluginFile(pluginShortName);
        if (file.exists() && file.length() > 0) {
            return file;
        }
        if (isAutoDownloadLostFileDisabled()) {
            LOGGER.warning(missingPluginFileMessage(pluginShortName, true));
            return file;
        }
        try {
            return downloadPlugin(file.getName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "download plugin " + file.getName() + " error", e);
            return file;
        }
    }

    private static boolean isAutoDownloadLostFileDisabled() {
        return PluginCoreDAO.getInstance().loadSnapshot().getSetting().isDisableAutoDownloadLostFile();
    }

    public static String missingPluginFileMessage(String pluginShortName) {
        return missingPluginFileMessage(pluginShortName, isAutoDownloadLostFileDisabled());
    }

    static String missingPluginFileMessage(String pluginShortName, boolean autoDownloadDisabled) {
        if (autoDownloadDisabled) {
            return "Plugin file not found: " + pluginShortName
                    + ". Automatic plugin download is disabled by disableAutoDownloadLostFile.";
        }
        return "Plugin file not found: " + pluginShortName;
    }

    private static File downloadPluginByUrl(String url, String fileName) throws Exception {
        LOGGER.info("download plugin " + fileName + " from " + url);
        File downloadFile = downloadPluginFile(fileName);
        File parentFile = downloadFile.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        copyInputStreamToFile(HttpUtils.doGetRequest(url, new HashMap<>()), downloadFile.toString());
        if (downloadFile.length() == 0) {
            throw new RuntimeException("Download error");
        }
        return downloadFile;
    }

    static File downloadPluginFile(String fileName) {
        return downloadPluginFile(fileName, EnvKit.isFaaSMode(), PluginConfig.getInstance().getMasterPort(),
                PluginConfig.getInstance().getPluginBasePath());
    }

    static File downloadPluginFile(String fileName, boolean faaSMode, int masterPort, String pluginBasePath) {
        if (!faaSMode) {
            return new File(pluginBasePath + "/" + fileName);
        }
        return new File(PluginConfig.getFaaSRuntimeRoot(masterPort) + "/plugins/installed-plugins/" + fileName);
    }

    public static File downloadPlugin(String fileName) throws Exception {
        String downloadUrl = "https://dl.zrlog.com/plugin/" + fileName;
        return downloadPluginByUrl(downloadUrl + "?v=" + System.currentTimeMillis(), fileName);
    }
}
