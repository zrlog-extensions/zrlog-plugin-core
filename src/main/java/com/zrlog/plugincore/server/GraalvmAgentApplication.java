package com.zrlog.plugincore.server;

import com.google.gson.Gson;
import com.hibegin.common.util.Pid;
import com.hibegin.http.server.util.NativeImageUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.plugin.common.PluginNativeImageUtils;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginCoreSetting;
import com.zrlog.plugincore.server.config.PluginVO;

import java.io.File;
import java.io.IOException;

public class GraalvmAgentApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        PluginNativeImageUtils.usedGsonObject();
        new Gson().toJson(new PluginVO());
        new Gson().toJson(new PluginCoreSetting());
        new Gson().toJson(new PluginCore());
        Application.init();
        Pid.get();
        String basePath = System.getProperty("user.dir").replace("/target", "");
        PathUtil.setRootPath(basePath);
        System.out.println("basePath = " + basePath);
        File file = new File(basePath.replace("\\target","") + "/src/main/frontend/build");
        NativeImageUtils.doLoopResourceLoad(file.listFiles(), file.getPath(), "/static");
        Application.nativeAgent = true;
        Application.main(args);
    }
}