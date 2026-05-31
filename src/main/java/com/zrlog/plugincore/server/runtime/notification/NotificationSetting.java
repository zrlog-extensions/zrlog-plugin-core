package com.zrlog.plugincore.server.runtime.notification;

import java.util.HashMap;
import java.util.Map;

public class NotificationSetting {

    private Map<String, NotificationProviderSetting> defaultProviders = new HashMap<>();

    public Map<String, NotificationProviderSetting> getDefaultProviders() {
        return defaultProviders;
    }

    public void setDefaultProviders(Map<String, NotificationProviderSetting> defaultProviders) {
        this.defaultProviders = defaultProviders;
    }
}
