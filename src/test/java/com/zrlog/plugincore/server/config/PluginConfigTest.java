package com.zrlog.plugincore.server.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginConfigTest {

    @Test
    public void shouldUseWritableFaaSRuntimeRootUnderTmp() {
        assertEquals("/tmp/9080", PluginConfig.getFaaSRuntimeRoot(9080));
    }

    @Test
    public void shouldKeepConfiguredPluginBasePathInFaaS() {
        assertEquals("/var/task/conf/plugins/installed-plugins",
                PluginConfig.resolvePluginBasePath(9080, "/var/task/conf/plugins/installed-plugins"));
    }
}
