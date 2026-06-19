package com.zrlog.plugincore.server.runtime.plugin.config;

import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.type.RunType;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void shouldConstructRuntimeConfigFromStartupValues() throws Exception {
        File pluginBasePath = File.createTempFile("zrlog-plugin-config", "");
        assertTrue(pluginBasePath.delete());

        BlogRunTime blogRunTime = new BlogRunTime();
        blogRunTime.setPath("/tmp/blog");
        blogRunTime.setVersion("4.0.0");

        PluginConfig pluginConfig = new PluginConfig(RunType.BLOG, new File("/tmp/db.properties"),
                19080, pluginBasePath.getPath(), blogRunTime);

        assertEquals(RunType.BLOG, pluginConfig.getRunType());
        assertEquals(new File("/tmp/db.properties"), pluginConfig.getDbPropertiesFile());
        assertEquals(19080, pluginConfig.getMasterPort());
        assertEquals(pluginBasePath.getPath(), pluginConfig.getPluginBasePath());
        assertEquals(blogRunTime, pluginConfig.getBlogRunTime());
        assertTrue(pluginBasePath.isDirectory());
        pluginBasePath.delete();
    }

    @Test
    public void shouldConstructHostConnectionFromStartupValues() {
        PluginHostConnection connection = new PluginHostConnection("http://127.0.0.1:8080/sub", "token", "Linux-amd64", "/sub/");

        assertEquals("http://127.0.0.1:8080/sub", connection.getBlogApiHomeUrl());
        assertEquals("token", connection.getBlogPluginToken());
        assertEquals("Linux-amd64", connection.getNativeInfo());
        assertEquals("/sub", connection.getContextPath());
    }
}
