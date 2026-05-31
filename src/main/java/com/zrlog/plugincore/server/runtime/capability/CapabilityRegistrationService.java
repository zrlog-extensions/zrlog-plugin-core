package com.zrlog.plugincore.server.runtime.capability;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.plugin.PluginSessions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CapabilityRegistrationService {

    private final CapabilityStore capabilityStore;
    private final Gson gson = new Gson();

    public CapabilityRegistrationService(CapabilityStore capabilityStore) {
        this.capabilityStore = capabilityStore;
    }

    public List<PluginCapability> registerCapabilitiesFromInitPayload(Plugin plugin, String initPayload) {
        if (plugin == null) {
            return new ArrayList<>();
        }
        List<PluginCapability> capabilities = parseExplicitCapabilities(plugin, initPayload);
        if (capabilities.isEmpty()) {
            capabilities = capabilitiesFromPlugin(plugin);
        }
        if (capabilities.isEmpty()) {
            capabilities = legacyCapabilities(plugin);
        }
        String pluginId = plugin.getId();
        if (pluginId != null) {
            capabilityStore.replacePluginCapabilities(pluginId,
                    Arrays.asList(plugin.getShortName(), PluginSessions.nameOrShortName(plugin)),
                    capabilities);
        }
        return capabilities;
    }

    public List<PluginCapability> legacyCapabilities(Plugin plugin) {
        List<PluginCapability> capabilities = new ArrayList<>();
        if (plugin == null || plugin.getServices() == null) {
            return capabilities;
        }
        for (String serviceName : plugin.getServices()) {
            Optional<PluginCapability> capability = CapabilityStore.fromLegacyService(plugin.getId(), PluginSessions.nameOrShortName(plugin), serviceName);
            if (capability.isPresent()) {
                capabilities.add(capability.get());
            }
        }
        return capabilities;
    }

    private List<PluginCapability> capabilitiesFromPlugin(Plugin plugin) {
        if (plugin == null || plugin.getCapabilities() == null) {
            return new ArrayList<>();
        }
        return normalizeCapabilities(plugin, plugin.getCapabilities());
    }

    private List<PluginCapability> parseExplicitCapabilities(Plugin plugin, String initPayload) {
        List<PluginCapability> capabilities = new ArrayList<>();
        if (initPayload == null || initPayload.trim().isEmpty()) {
            return capabilities;
        }
        JsonElement root = new JsonParser().parse(initPayload);
        if (!root.isJsonObject()) {
            return capabilities;
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement capabilityElement = object.get("capabilities");
        if (capabilityElement == null || !capabilityElement.isJsonArray()) {
            return capabilities;
        }
        JsonArray array = capabilityElement.getAsJsonArray();
        for (JsonElement item : array) {
            PluginCapability capability = gson.fromJson(item, PluginCapability.class);
            if (capability == null || capability.getKey() == null || capability.getKey().trim().isEmpty()) {
                continue;
            }
            capabilities.add(capability);
        }
        return normalizeCapabilities(plugin, capabilities);
    }

    private List<PluginCapability> normalizeCapabilities(Plugin plugin, List<PluginCapability> input) {
        List<PluginCapability> capabilities = new ArrayList<>();
        if (input == null) {
            return capabilities;
        }
        for (PluginCapability capability : input) {
            if (capability == null || isBlank(capability.getKey())) {
                continue;
            }
            capability.setPluginId(plugin.getId());
            if (isBlank(capability.getPluginName())) {
                capability.setPluginName(PluginSessions.nameOrShortName(plugin));
            }
            capabilities.add(capability);
        }
        fillMissingServiceNames(plugin, capabilities);
        return capabilities;
    }

    private void fillMissingServiceNames(Plugin plugin, List<PluginCapability> capabilities) {
        if (plugin == null || plugin.getServices() == null || plugin.getServices().isEmpty()) {
            return;
        }
        Set<String> services = new HashSet<String>(plugin.getServices());
        List<PluginCapability> missingServiceCapabilities = capabilities.stream()
                .filter(item -> Objects.equals("service", item.getType()))
                .filter(item -> item.getServiceName() == null || item.getServiceName().trim().isEmpty())
                .collect(Collectors.toList());
        for (PluginCapability capability : missingServiceCapabilities) {
            String serviceName = inferServiceName(services, capability);
            if (serviceName != null) {
                capability.setServiceName(serviceName);
            }
        }
        // Compatibility fallback for plugins built before common emits serviceName. Remove when service providers require common >= 4.0.2.
        List<String> legacyAliases = services.stream()
                .filter(item -> !CapabilityStore.canGenerateLegacyCapability(item))
                .collect(Collectors.toList());
        if (missingServiceCapabilities.size() == 1 && legacyAliases.size() == 1) {
            PluginCapability capability = missingServiceCapabilities.get(0);
            if (capability.getServiceName() == null || capability.getServiceName().trim().isEmpty()) {
                capability.setServiceName(legacyAliases.get(0));
            }
        }
    }

    private String inferServiceName(Set<String> services, PluginCapability capability) {
        if (services.contains(capability.getKey())) {
            return capability.getKey();
        }
        if (capability.getKey() != null && capability.getKey().endsWith(".upload") && services.contains("uploadService")) {
            return "uploadService";
        }
        if (capability.getKey() != null && capability.getKey().endsWith(".uploadPrivate") && services.contains("uploadToPrivateService")) {
            return "uploadToPrivateService";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
