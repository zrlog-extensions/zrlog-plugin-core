package com.zrlog.plugincore.server.runtime;

import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;

public class PluginCoreRunMode {

    private PluginCoreRunMode() {
    }

    public static boolean isNativeAgent() {
        return RunConstants.runType == RunType.AGENT;
    }

    public static boolean shouldBootstrapRuntimeWorkers() {
        return !isNativeAgent();
    }
}
