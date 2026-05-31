package com.zrlog.plugincore.server.runtime.notification;

import com.zrlog.plugin.message.PluginCapability;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationProviderResolverTest {

    @Test
    public void shouldResolveConfiguredProvider() {
        NotificationProviderResolver resolver = new NotificationProviderResolver();
        NotificationSetting setting = new NotificationSetting();
        NotificationProviderSetting providerSetting = new NotificationProviderSetting();
        providerSetting.setPluginId("email-b");
        providerSetting.setCapabilityKey("notification.email.send");
        setting.getDefaultProviders().put("email", providerSetting);

        PluginCapability resolved = resolver.resolve("email", Arrays.asList(
                provider("email-a", "Email A"),
                provider("email-b", "Email B")
        ), setting).get();

        assertEquals("email-b", resolved.getPluginId());
        assertFalse(resolver.reviewRequired("email", Collections.singletonList(provider("email-a", "Email A")), setting));
    }

    @Test
    public void shouldPickStableProviderAndMarkReviewRequired() {
        NotificationProviderResolver resolver = new NotificationProviderResolver();

        PluginCapability resolved = resolver.resolve("email", Arrays.asList(
                provider("email-b", "Email B"),
                provider("email-a", "Email A")
        ), new NotificationSetting()).get();

        assertEquals("email-a", resolved.getPluginId());
        assertTrue(resolver.reviewRequired("email", Arrays.asList(
                provider("email-b", "Email B"),
                provider("email-a", "Email A")
        ), new NotificationSetting()));
    }

    @Test
    public void shouldMarkReviewRequiredWhenConfiguredProviderIsGone() {
        NotificationProviderResolver resolver = new NotificationProviderResolver();
        NotificationSetting setting = new NotificationSetting();
        NotificationProviderSetting providerSetting = new NotificationProviderSetting();
        providerSetting.setPluginId("email-missing");
        providerSetting.setCapabilityKey("notification.email.send");
        setting.getDefaultProviders().put("email", providerSetting);

        assertTrue(resolver.reviewRequired("email", Arrays.asList(
                provider("email-b", "Email B"),
                provider("email-a", "Email A")
        ), setting));
    }

    @Test
    public void shouldIgnoreDisabledProvider() {
        NotificationProviderResolver resolver = new NotificationProviderResolver();
        PluginCapability disabled = provider("email-a", "Email A");
        disabled.setEnabled(Boolean.FALSE);

        PluginCapability resolved = resolver.resolve("email", Arrays.asList(
                disabled,
                provider("email-b", "Email B")
        ), new NotificationSetting()).get();

        assertEquals("email-b", resolved.getPluginId());
        assertFalse(resolver.reviewRequired("email", Arrays.asList(
                disabled,
                provider("email-b", "Email B")
        ), new NotificationSetting()));
    }

    private PluginCapability provider(String pluginId, String pluginName) {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginName);
        capability.setKey("notification.email.send");
        capability.setType("notification_channel");
        capability.setExposure(Arrays.asList("notification"));
        capability.setChannel("email");
        capability.setEnabled(Boolean.TRUE);
        return capability;
    }
}
