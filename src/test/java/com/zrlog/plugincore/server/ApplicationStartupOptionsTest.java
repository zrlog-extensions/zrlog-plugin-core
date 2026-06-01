package com.zrlog.plugincore.server;

import org.junit.Test;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginHostConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApplicationStartupOptionsTest {

    @Test
    public void shouldParseStandaloneDefaults() throws Exception {
        ApplicationStartupOptions options = ApplicationStartupOptions.parse(null);

        assertEquals(9089, options.getHttpPort());
        assertFalse(options.hasExternalDbProperties());
        assertTrue(options.getDbProperties().exists());
        assertEquals(PluginHostConnection.DEFAULT_BLOG_API_HOME_URL, options.getBlogApiHomeUrl());
        assertEquals("", options.getBlogPluginToken());
        assertEquals("", options.getNativeInfo());
        assertEquals(-1, options.getListenBlogPort());
    }

    @Test
    public void shouldParseBlogRuntimeArgs() throws Exception {
        ApplicationStartupOptions options = ApplicationStartupOptions.parse(new String[]{
                "9089",
                "19080",
                "/tmp/blog-db.properties",
                "/tmp/plugins",
                "19081",
                "/tmp/blog-runtime",
                "4.0.0",
                "8080",
                "token",
                "-",
                "#/admin"
        });

        assertEquals(9089, options.getHttpPort());
        assertEquals(19080, options.getMasterPort());
        assertEquals("/tmp/blog-db.properties", options.getDbProperties().getPath());
        assertEquals("/tmp/plugins", options.getPluginPath());
        assertEquals("/tmp/blog-runtime", options.getBlogRunTime().getPath());
        assertEquals("4.0.0", options.getBlogRunTime().getVersion());
        assertTrue(options.hasExternalDbProperties());
        assertEquals(19081, options.getListenBlogPort());
        assertEquals("http://127.0.0.1:8080/admin", options.getBlogApiHomeUrl());
        assertEquals("token", options.getBlogPluginToken());
        assertEquals("", options.getNativeInfo());
    }
}
