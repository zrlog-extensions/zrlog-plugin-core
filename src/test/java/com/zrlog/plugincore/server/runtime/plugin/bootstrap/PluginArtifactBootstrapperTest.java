package com.zrlog.plugincore.server.runtime.plugin.bootstrap;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginVO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginArtifactBootstrapperTest {

    @Test
    public void shouldResolveInstalledArtifactPluginIdByMapKeyOrShortName() {
        PluginCore pluginCore = new PluginCore();
        PluginVO pluginVO = new PluginVO();
        Plugin plugin = new Plugin();
        plugin.setId("plugin-id");
        plugin.setShortName("reminder");
        pluginVO.setPlugin(plugin);
        pluginCore.getPluginInfoMap().put("legacy-key", pluginVO);

        assertEquals("plugin-id", PluginArtifactBootstrapper.pluginIdForInstalledArtifact(pluginCore, "reminder"));
        assertFalse(PluginArtifactBootstrapper.pluginIdForInstalledArtifact(pluginCore, "email").trim().isEmpty());
    }

    @Test
    public void shouldSkipMissingPluginDownloadDuringOnDemandBootstrap() {
        PluginCore pluginCore = new PluginCore();
        pluginCore.getSetting().getRuntime().setOnDemandEnabled(true);

        assertFalse(PluginArtifactBootstrapper.shouldDownloadMissingPluginFilesDuringBootstrap(pluginCore));
    }

    @Test
    public void shouldTreatMissingSettingAsOnDemandBootstrap() {
        assertFalse(PluginArtifactBootstrapper.shouldDownloadMissingPluginFilesDuringBootstrap(null));
    }

    @Test
    public void shouldDownloadMissingPluginsDuringStartupBootstrap() {
        PluginCore pluginCore = new PluginCore();
        pluginCore.getSetting().getRuntime().setOnDemandEnabled(false);

        assertTrue(PluginArtifactBootstrapper.shouldDownloadMissingPluginFilesDuringBootstrap(pluginCore));
    }

    @Test
    public void shouldSkipStartupMissingPluginDownloadWhenAutoDownloadDisabled() {
        PluginCore pluginCore = new PluginCore();
        pluginCore.getSetting().getRuntime().setOnDemandEnabled(false);
        pluginCore.getSetting().setDisableAutoDownloadLostFile(true);

        assertFalse(PluginArtifactBootstrapper.shouldDownloadMissingPluginFilesDuringBootstrap(pluginCore));
    }
}
