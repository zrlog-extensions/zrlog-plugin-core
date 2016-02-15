package com.zrlog.plugincore.server.util;

import com.zrlog.plugin.common.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DevUtil {

    private static final Properties prop = new Properties();
    private static final Logger LOGGER = LoggerUtil.getLogger(DevUtil.class);

    static {
        try {
            InputStream in = DevUtil.class.getResourceAsStream("/dev.properties");
            if (in != null) {
                prop.load(in);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    public static String pluginHome() {
        Object home = prop.get("plugin.home");
        if (home != null) {
            return home.toString();
        }
        throw new RuntimeException("dev.properties setting error");
    }

    public static String blogVersion() {
        Object obj = prop.get("blog.version");
        if (obj != null) {
            return obj.toString();
        }
        throw new RuntimeException("dev.properties setting error");
    }

    public static String blogRuntimePath() {
        Object obj = prop.get("blog.runtimePath");
        if (obj != null) {
            return obj.toString();
        }
        throw new RuntimeException("dev.properties setting error");
    }
}
