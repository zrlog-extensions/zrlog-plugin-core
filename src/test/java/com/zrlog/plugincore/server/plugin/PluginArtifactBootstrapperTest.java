package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginVO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
}
