package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.EnvKit;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.type.PluginStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xiaochun on 2016/2/11.
 */
public class PluginUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginUtil.class);

    private static PluginScanRunnable pluginScanRunnable;

    public static void loadPlugins(PluginCore pluginCore) {
        try {
            pluginScanRunnable = new PluginScanRunnable(pluginCore);
            if (EnvKit.isFaaSMode()) {
                pluginScanRunnable.run();
            } else {
                Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(pluginScanRunnable, 0, 5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "start plugin exception ", e);
        }

    }

    public static String getPluginName(File file) {
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


    public static void loadPlugin(final File pluginFile, String pluginId) {
        pluginScanRunnable.loadPlugin(pluginFile, pluginId);
    }

    public static void registerPlugin(PluginStatus pluginStatus, IOSession session) {
        if (Objects.isNull(session)) {
            return;
        }
        if (pluginStatus != PluginStatus.START && pluginStatus != PluginStatus.WAIT_INSTALL) {
            throw new IllegalArgumentException("status must be not " + pluginStatus);
        }
        PluginVO pluginVO = new PluginVO();
        pluginVO.setStatus(pluginStatus);
        pluginVO.setPlugin(session.getPlugin());
        PluginCoreDAO.getInstance().getPluginInfoMap().put(session.getPlugin().getShortName(), pluginVO);
        PluginConfig.getInstance().getSessionMap().put(session.getPlugin().getId(), session);
    }

    public static void stopPlugin(String pluginName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByName(pluginName);
        String pluginId = pluginVO.getPlugin().getId();
        IOSession session = PluginConfig.getInstance().getSessionMap().get(pluginId);
        session.close();

        pluginScanRunnable.destroy(pluginName);

        PluginCoreDAO.getInstance().getPluginVOByName(pluginName).setStatus(PluginStatus.STOP);
    }

    public static void deletePlugin(String pluginName) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByName(pluginName);
        if (pluginVO != null) {
            String pluginId = pluginVO.getPlugin().getId();
            IOSession session = PluginConfig.getInstance().getSessionMap().get(pluginId);
            if (session != null) {
                session.close();
                pluginScanRunnable.destroy(pluginName);
            }
            File pluginFile = getPluginFile(pluginName);
            if (pluginFile.exists()) {
                pluginFile.delete();
            }
        }
        PluginCoreDAO.getInstance().getPluginInfoMap().remove(pluginName);
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
                    //ignore
                }
            }

        }
    }

    public static File getPluginFile(String pluginName) {
        String filename = StringUtils.isEmpty(Application.NATIVE_INFO) ? pluginName + ".jar" :
                pluginName + "-" + Application.NATIVE_INFO + (Application.NATIVE_INFO.contains("Window") ? ".exe" : ".bin");
        return new File(PluginConfig.getInstance().getPluginBasePath() + "/" + filename);
    }

    private static File downloadPluginByUrl(String url, String fileName) throws Exception {
        LOGGER.info("download plugin " + fileName);
        File downloadFile = new File(PluginConfig.getInstance().getPluginBasePath() + "/" + fileName);
        copyInputStreamToFile(HttpUtils.doGetRequest(url, new HashMap<>()), downloadFile.toString());
        if (downloadFile.length() == 0) {
            throw new RuntimeException("Download error");
        }
        return downloadFile;
    }

    public static File downloadPlugin(String fileName) throws Exception {
        String downloadUrl = "https://dl.zrlog.com/plugin/" + fileName;
        return downloadPluginByUrl(downloadUrl, fileName);

    }

    public static void main(String[] args) throws Exception {
        File file = downloadPlugin("oss.jar");
        System.out.println(file);
    }

    public static List<Plugin> allRunningPlugins() {
        List<Plugin> allPlugins = new ArrayList<>();
        for (PluginVO pluginEntry : PluginCoreDAO.getInstance().getAllPluginVO()) {
            if (!isRunningByPluginId(pluginEntry.getPlugin().getId())) {
                continue;
            }
            if (StringUtils.isEmpty(pluginEntry.getPlugin().getPreviewImageBase64())) {
                pluginEntry.getPlugin().setPreviewImageBase64("");
            }
            allPlugins.add(pluginEntry.getPlugin());
        }
        return allPlugins;
    }

    public static boolean allRunning() {
        return pluginScanRunnable.getAllRunnablePluginIds().stream().allMatch(PluginUtil::isRunningByPluginId);
    }

    public static boolean isRunningByPluginId(String pluginId) {
        return PluginConfig.getInstance().getAllSessions().stream().anyMatch(e -> e.getPlugin().getId().equals(pluginId));
    }
}
