package com.zrlog.plugincore.server.runtime.plugin.bootstrap;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.plugin.artifact.PluginFiles;
import com.zrlog.plugincore.server.vo.PluginVO;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginMetadataBootstrapperTest {

    private final PluginMetadataBootstrapper metadataBootstrapper =
            new PluginMetadataBootstrapper(null, pluginShortName -> {
            });

    @Test
    public void shouldSkipMissingPluginFile() {
        assertFalse(metadataBootstrapper.shouldStartPluginFileForMetadata(new File("/tmp/missing-plugin.jar"),
                "plugin-id", new PluginCore()));
    }

    @Test
    public void shouldStartWhenSnapshotHasNoPluginMetadata() throws Exception {
        File pluginFile = Files.createTempFile("metadata-bootstrap", ".jar").toFile();
        try {
            Files.write(pluginFile.toPath(), new byte[]{1});

            assertTrue(metadataBootstrapper.shouldStartPluginFileForMetadata(pluginFile, "plugin-id", new PluginCore()));
        } finally {
            pluginFile.delete();
        }
    }

    @Test
    public void shouldSkipWhenPluginFileMd5MatchesSnapshot() throws Exception {
        File pluginFile = Files.createTempFile("metadata-bootstrap", ".jar").toFile();
        try {
            Files.write(pluginFile.toPath(), new byte[]{1});
            PluginCore pluginCore = new PluginCore();
            Plugin plugin = new Plugin();
            plugin.setId("plugin-id");
            plugin.setShortName(PluginFiles.getPluginShortName(pluginFile));
            PluginVO pluginVO = new PluginVO();
            pluginVO.setPlugin(plugin);
            pluginVO.setFileMd5(PluginFiles.pluginFileMd5(pluginFile));
            pluginCore.getPluginInfoMap().put(plugin.getShortName(), pluginVO);

            assertFalse(metadataBootstrapper.shouldStartPluginFileForMetadata(pluginFile, "plugin-id", pluginCore));
        } finally {
            pluginFile.delete();
        }
    }
}
