package com.zrlog.plugincore.server.runtime.scheduler;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BearerSchedulerAuthTest {

    @Test
    public void shouldVerifyBearerToken() {
        BearerSchedulerAuth auth = new BearerSchedulerAuth();

        assertTrue(auth.verify(Collections.singletonList(provider("secret", true)), "Bearer secret"));
    }

    @Test
    public void shouldRejectWrongToken() {
        BearerSchedulerAuth auth = new BearerSchedulerAuth();

        assertFalse(auth.verify(Collections.singletonList(provider("secret", true)), "Bearer wrong"));
    }

    @Test
    public void shouldRejectDisabledProvider() {
        BearerSchedulerAuth auth = new BearerSchedulerAuth();

        assertFalse(auth.verify(Collections.singletonList(provider("secret", false)), "Bearer secret"));
    }

    @Test
    public void shouldRejectNonBearerHeader() {
        BearerSchedulerAuth auth = new BearerSchedulerAuth();

        assertFalse(auth.verify(Collections.singletonList(provider("secret", true)), "Basic secret"));
    }

    private SchedulerProviderSetting provider(String secret, boolean enabled) {
        SchedulerProviderSetting provider = new SchedulerProviderSetting();
        provider.setId("default");
        provider.setSecret(secret);
        provider.setEnabled(enabled);
        return provider;
    }
}
