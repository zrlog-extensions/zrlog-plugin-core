package com.zrlog.plugincore.server.vo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class PluginCoreSettingTest {

    @Test
    public void shouldCreateDefaultSchedulerProvider() {
        PluginCoreSetting setting = new PluginCoreSetting();

        assertEquals(1, setting.getScheduler().getProviders().size());
        assertEquals("default", setting.getScheduler().getProviders().get(0).getId());
        assertEquals(Boolean.FALSE, setting.getScheduler().getProviders().get(0).getEnabled());
        assertNotNull(setting.getScheduler().getProviders().get(0).getSecret());
        assertFalse(setting.getScheduler().getProviders().get(0).getSecret().isEmpty());
    }

    @Test
    public void shouldCreateDefaultNotificationSetting() {
        PluginCoreSetting setting = new PluginCoreSetting();

        assertNotNull(setting.getNotification());
        assertNotNull(setting.getNotification().getDefaultProviders());
    }

    @Test
    public void shouldCreateDefaultRuntimeSetting() {
        PluginCoreSetting setting = new PluginCoreSetting();

        assertEquals(Boolean.TRUE, setting.getRuntime().getOnDemandEnabled());
        assertEquals(Boolean.TRUE, setting.getRuntime().getAutoDownloadMissingPluginFileEnabled());
        assertEquals(Boolean.TRUE, setting.getRuntime().getIdleStopEnabled());
        assertEquals(Long.valueOf(300L), setting.getRuntime().getIdleTimeoutSeconds());
        assertEquals(Long.valueOf(30L), setting.getRuntime().getIdleScanIntervalSeconds());
    }

    @Test
    public void shouldKeepLegacyAutoDownloadSettingCompatible() {
        PluginCoreSetting setting = new PluginCoreSetting();

        setting.setDisableAutoDownloadLostFile(true);

        assertFalse(setting.isAutoDownloadMissingPluginFileEnabled());
        assertEquals(Boolean.FALSE, setting.getRuntime().getAutoDownloadMissingPluginFileEnabled());
    }

    @Test
    public void shouldPreferRuntimeAutoDownloadSetting() {
        PluginCoreSetting setting = new PluginCoreSetting();
        setting.getRuntime().setAutoDownloadMissingPluginFileEnabled(false);

        assertFalse(setting.isAutoDownloadMissingPluginFileEnabled());
        assertEquals(Boolean.TRUE, setting.isDisableAutoDownloadLostFile());
    }
}
