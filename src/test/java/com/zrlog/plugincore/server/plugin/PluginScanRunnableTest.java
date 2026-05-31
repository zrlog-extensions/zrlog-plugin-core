package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.config.PluginCore;
import com.zrlog.plugincore.server.config.PluginVO;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PluginScanRunnableTest {

    @Test
    public void shouldUsePluginHomeAsWorkingDirectoryForNativePlugin() {
        String userDir = "/tmp/9080/reminder/usr/";
        String tmpDir = "/tmp/9080/reminder/tmp/";
        File pluginFile = new File("/var/task/conf/plugins/installed-plugins/reminder-Linux-amd64.bin");

        PluginScanRunnable.LaunchCommand command = PluginScanRunnable.buildLaunchCommand(
                pluginFile, 9080, "plugin-id", userDir, tmpDir, "", "/opt/java"
        );

        assertEquals(pluginFile.toString(), command.program);
        assertEquals(Arrays.asList("9080", "plugin-id"), command.args);
        assertEquals(new File(userDir), command.workingDirectory);
        assertEquals(userDir, command.environment.get("HOME"));
        assertEquals(tmpDir, command.environment.get("TMPDIR"));
        assertEquals("native", PluginScanRunnable.runtimeMode(pluginFile));
    }

    @Test
    public void shouldKeepJarSystemPropertiesAndUseSameWorkingDirectory() {
        String userDir = "/tmp/9080/reminder/usr/";
        String tmpDir = "/tmp/9080/reminder/tmp/";
        File pluginFile = new File("/var/task/conf/plugins/installed-plugins/reminder.jar");

        PluginScanRunnable.LaunchCommand command = PluginScanRunnable.buildLaunchCommand(
                pluginFile, 9080, "plugin-id", userDir, tmpDir, "-Dfile.encoding=UTF-8 -Xmx32m", "/opt/java"
        );

        assertEquals("/opt/java/bin/java", command.program);
        assertEquals(Arrays.asList(
                "-Djava.io.tmpdir=" + tmpDir,
                "-Duser.dir=" + userDir,
                "-Duser.home=" + userDir,
                "-Dfile.encoding=UTF-8",
                "-Xmx32m",
                "-jar",
                pluginFile.toString(),
                "9080",
                "plugin-id"
        ), command.args);
        assertEquals(new File(userDir), command.workingDirectory);
        assertEquals(userDir, command.environment.get("HOME"));
        assertEquals(tmpDir, command.environment.get("TMPDIR"));
        assertEquals("process", PluginScanRunnable.runtimeMode(pluginFile));
    }

    @Test
    public void shouldResolvePluginExecutableBeforeChangingWorkingDirectory() {
        File pluginFile = new File("conf/plugins/installed-plugins/reminder-Linux-amd64.bin");

        PluginScanRunnable.LaunchCommand command = PluginScanRunnable.buildLaunchCommand(
                pluginFile, 9080, "plugin-id", "/tmp/9080/reminder/usr/", "/tmp/9080/reminder/tmp/", "", "/opt/java"
        );

        assertEquals(pluginFile.getAbsolutePath(), command.program);
    }

    @Test
    public void shouldNotDestroyProcessWhenOutputStreamEnds() throws Exception {
        PluginScanRunnable runnable = new PluginScanRunnable();
        FakeProcess process = new FakeProcess();

        runnable.drainProcessOutput(process, new ByteArrayInputStream(new byte[0]), "reminder", "PINFO",
                "plugin-id", new AtomicBoolean(false));

        assertFalse(process.destroyed);
    }

    @Test
    public void shouldResolveInstalledArtifactPluginIdByMapKeyOrShortName() {
        PluginCore pluginCore = new PluginCore();
        PluginVO pluginVO = new PluginVO();
        Plugin plugin = new Plugin();
        plugin.setId("plugin-id");
        plugin.setShortName("reminder");
        pluginVO.setPlugin(plugin);
        pluginCore.getPluginInfoMap().put("legacy-key", pluginVO);

        assertEquals("plugin-id", PluginScanRunnable.pluginIdForInstalledArtifact(pluginCore, "reminder"));
        assertFalse(PluginScanRunnable.pluginIdForInstalledArtifact(pluginCore, "email").trim().isEmpty());
    }

    private static class FakeProcess extends Process {

        private boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
