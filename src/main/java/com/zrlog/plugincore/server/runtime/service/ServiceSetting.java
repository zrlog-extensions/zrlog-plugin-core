package com.zrlog.plugincore.server.runtime.service;

import java.util.HashMap;
import java.util.Map;

public class ServiceSetting {

    private Map<String, ServiceProviderSetting> defaultProviders = new HashMap<String, ServiceProviderSetting>();

    public Map<String, ServiceProviderSetting> getDefaultProviders() {
        if (defaultProviders == null) {
            defaultProviders = new HashMap<String, ServiceProviderSetting>();
        }
        return defaultProviders;
    }

    public void setDefaultProviders(Map<String, ServiceProviderSetting> defaultProviders) {
        this.defaultProviders = defaultProviders;
    }
}
