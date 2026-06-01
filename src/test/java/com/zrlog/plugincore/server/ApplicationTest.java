package com.zrlog.plugincore.server;

import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.runtime.PluginCoreRunMode;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApplicationTest {

    @Test
    public void shouldConfigureWritableFaaSRuntimeRoot() {
        String[] keys = new String[]{
                "sws.root.path",
                "sws.log.path",
                "sws.cache.path",
                "sws.temp.path",
                "java.io.tmpdir",
                "user.home",
                "user.dir"
        };
        Map<String, String> previous = snapshot(keys);
        try {
            ApplicationEnvironment.configureWritableRuntimeRoot(19080);

            assertEquals("/tmp/19080/plugin-core", System.getProperty("sws.root.path"));
            assertEquals("/tmp/19080/plugin-core/log", System.getProperty("sws.log.path"));
            assertEquals("/tmp/19080/plugin-core/cache", System.getProperty("sws.cache.path"));
            assertEquals("/tmp/19080/plugin-core/temp", System.getProperty("sws.temp.path"));
            assertEquals("/tmp/19080/plugin-core/tmp", System.getProperty("java.io.tmpdir"));
            assertEquals("/tmp/19080/plugin-core/usr", System.getProperty("user.home"));
            assertEquals("/tmp/19080/plugin-core", System.getProperty("user.dir"));
        } finally {
            restore(previous);
        }
    }

    @Test
    public void shouldSkipRuntimeWorkerBootstrapInNativeAgentMode() {
        RunType previous = RunConstants.runType;
        try {
            RunConstants.runType = RunType.AGENT;
            assertFalse(PluginCoreRunMode.shouldBootstrapRuntimeWorkers());

            RunConstants.runType = RunType.BLOG;
            assertTrue(PluginCoreRunMode.shouldBootstrapRuntimeWorkers());
        } finally {
            RunConstants.runType = previous;
        }
    }

    private Map<String, String> snapshot(String[] keys) {
        Map<String, String> values = new HashMap<>();
        for (String key : keys) {
            values.put(key, System.getProperty(key));
        }
        return values;
    }

    private void restore(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
