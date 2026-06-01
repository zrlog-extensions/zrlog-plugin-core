package com.zrlog.plugincore.server.runtime.service;

import com.zrlog.plugin.message.PluginCapability;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServiceProviderResolver {

    public Optional<PluginCapability> resolve(String serviceName, List<PluginCapability> capabilities, ServiceSetting setting) {
        List<PluginCapability> providers = providersFor(serviceName, capabilities);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        if (setting != null && setting.getDefaultProviders() != null) {
            ServiceProviderSetting configured = setting.getDefaultProviders().get(serviceName);
            if (configured != null) {
                Optional<PluginCapability> configuredCapability = providers.stream()
                        .filter(item -> Objects.equals(configured.getPluginId(), item.getPluginId()))
                        .filter(item -> Objects.equals(configured.getCapabilityKey(), item.getKey()))
                        .findFirst();
                if (configuredCapability.isPresent()) {
                    return configuredCapability;
                }
            }
        }
        return Optional.of(providers.get(0));
    }

    public boolean reviewRequired(String serviceName, List<PluginCapability> capabilities, ServiceSetting setting) {
        List<PluginCapability> providers = providersFor(serviceName, capabilities);
        if (providers.size() <= 1) {
            return false;
        }
        if (setting == null || setting.getDefaultProviders() == null) {
            return true;
        }
        ServiceProviderSetting configured = setting.getDefaultProviders().get(serviceName);
        if (configured == null) {
            return true;
        }
        return providers.stream()
                .noneMatch(item -> Objects.equals(configured.getPluginId(), item.getPluginId())
                        && Objects.equals(configured.getCapabilityKey(), item.getKey()));
    }

    public List<PluginCapability> providersFor(String serviceName, List<PluginCapability> capabilities) {
        return capabilities.stream()
                .filter(item -> Objects.equals("service", item.getType()))
                .filter(item -> item.getExposure() != null && item.getExposure().contains("internal"))
                .filter(item -> matchesServiceName(serviceName, item))
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .sorted(Comparator.comparing(this::stableProviderKey))
                .collect(Collectors.toList());
    }

    private boolean matchesServiceName(String serviceName, PluginCapability capability) {
        return Objects.equals(serviceName, serviceNameFor(capability));
    }

    public String serviceNameFor(PluginCapability capability) {
        if (capability == null) {
            return null;
        }
        if (capability.getServiceName() != null && !capability.getServiceName().trim().isEmpty()) {
            return capability.getServiceName();
        }
        if (Boolean.TRUE.equals(capability.getLegacy())) {
            return capability.getKey();
        }
        return null;
    }

    private String stableProviderKey(PluginCapability capability) {
        String name = capability.getPluginName() == null ? "" : capability.getPluginName();
        String id = capability.getPluginId() == null ? "" : capability.getPluginId();
        String key = capability.getKey() == null ? "" : capability.getKey();
        return name + ":" + id + ":" + key;
    }
}
