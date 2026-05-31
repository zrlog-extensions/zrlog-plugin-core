package com.zrlog.plugincore.server.runtime.notification;

import com.zrlog.plugin.message.PluginCapability;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class NotificationProviderResolver {

    public Optional<PluginCapability> resolve(String channel, List<PluginCapability> capabilities, NotificationSetting setting) {
        List<PluginCapability> providers = providersFor(channel, capabilities);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        if (setting != null && setting.getDefaultProviders() != null) {
            NotificationProviderSetting configured = setting.getDefaultProviders().get(channel);
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

    public boolean reviewRequired(String channel, List<PluginCapability> capabilities, NotificationSetting setting) {
        List<PluginCapability> providers = providersFor(channel, capabilities);
        if (providers.size() <= 1) {
            return false;
        }
        if (setting == null || setting.getDefaultProviders() == null) {
            return true;
        }
        NotificationProviderSetting configured = setting.getDefaultProviders().get(channel);
        if (configured == null) {
            return true;
        }
        return providers.stream()
                .noneMatch(item -> Objects.equals(configured.getPluginId(), item.getPluginId())
                        && Objects.equals(configured.getCapabilityKey(), item.getKey()));
    }

    private List<PluginCapability> providersFor(String channel, List<PluginCapability> capabilities) {
        return capabilities.stream()
                .filter(item -> Objects.equals("notification_channel", item.getType()))
                .filter(item -> item.getExposure() != null && item.getExposure().contains("notification"))
                .filter(item -> Objects.equals(channel, item.getChannel()))
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .sorted(Comparator.comparing(this::stableProviderKey))
                .collect(Collectors.toList());
    }

    private String stableProviderKey(PluginCapability capability) {
        String name = capability.getPluginName() == null ? "" : capability.getPluginName();
        String id = capability.getPluginId() == null ? "" : capability.getPluginId();
        return name + ":" + id;
    }
}
