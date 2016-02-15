package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.EnvKit;

public class LambdaEnv {

    public static void initLambdaEnv() {
        if (!EnvKit.isLambda()) {
            return;
        }
        System.getProperties().put("sws.log.path", "/tmp/log");
        System.getProperties().put("sws.temp.path", "/tmp/temp");
        System.getProperties().put("sws.cache.path", "/tmp/cache");
        System.getProperties().put("sws.static.path", "/tmp/static");
        System.getProperties().put("sws.conf.path", "/tmp/conf");
    }
}
