package com.zrlog.plugincore.server.runtime.capability;

import com.google.gson.Gson;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CapabilityStore {

    public static final String KEY = "plugin.runtime.capabilities";
    private static final int STORE_UPDATE_RETRIES = 3;
    private static final Pattern DOTTED_KEY_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+$");

    private final KvRepository kvStore;
    private final Gson gson = new Gson();

    public CapabilityStore(KvRepository kvStore) {
        this.kvStore = kvStore;
    }

    public void register(PluginCapability capability) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            CapabilityDocumentSnapshot snapshot = loadSnapshot();
            snapshot.getDocument().setItems(registerItems(snapshot.getDocument().getItems(), capability));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to register capability due to concurrent modification");
    }

    private List<PluginCapability> registerItems(List<PluginCapability> currentItems, PluginCapability capability) {
        List<PluginCapability> next = new ArrayList<>();
        for (PluginCapability item : currentItems) {
            if (!sameIdentity(item, capability)) {
                next.add(item);
            }
        }
        normalize(capability);
        next.add(capability);
        return next;
    }

    public void replacePluginCapabilities(String pluginId, List<PluginCapability> capabilities) {
        replacePluginCapabilities(pluginId, (List<String>) null, capabilities);
    }

    public void replacePluginCapabilities(String pluginId, String pluginName, List<PluginCapability> capabilities) {
        replacePluginCapabilities(pluginId, pluginName == null ? null : Arrays.asList(pluginName), capabilities);
    }

    public void replacePluginCapabilities(String pluginId, List<String> pluginNames, List<PluginCapability> capabilities) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            CapabilityDocumentSnapshot snapshot = loadSnapshot();
            snapshot.getDocument().setItems(replacePluginCapabilityItems(
                    snapshot.getDocument().getItems(), pluginId, pluginNames, capabilities));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to replace plugin capabilities due to concurrent modification");
    }

    private List<PluginCapability> replacePluginCapabilityItems(List<PluginCapability> currentItems,
                                                                String pluginId,
                                                                List<String> pluginNames,
                                                                List<PluginCapability> capabilities) {
        List<PluginCapability> next = new ArrayList<>();
        for (PluginCapability item : currentItems) {
            if (!samePlugin(pluginId, pluginNames, item)) {
                next.add(item);
            }
        }
        if (capabilities != null) {
            for (PluginCapability capability : capabilities) {
                normalize(capability);
                next.add(capability);
            }
        }
        return next;
    }

    private boolean samePlugin(String pluginId, List<String> pluginNames, PluginCapability capability) {
        if (Objects.equals(pluginId, capability.getPluginId())) {
            return true;
        }
        if (pluginNames == null) {
            return false;
        }
        for (String pluginName : pluginNames) {
            if (pluginName != null && !pluginName.trim().isEmpty()
                    && Objects.equals(pluginName, capability.getPluginName())) {
                return true;
            }
        }
        return false;
    }

    public List<PluginCapability> listAll() {
        return loadDocument().getItems();
    }

    public List<PluginCapability> listByPluginId(String pluginId) {
        return listAll().stream()
                .filter(item -> Objects.equals(pluginId, item.getPluginId()))
                .collect(Collectors.toList());
    }

    public List<PluginCapability> listByExposure(String exposure) {
        return listAll().stream()
                .filter(item -> item.getExposure() != null && item.getExposure().contains(exposure))
                .collect(Collectors.toList());
    }

    public List<PluginCapability> listByType(String type) {
        return listAll().stream()
                .filter(item -> Objects.equals(type, item.getType()))
                .collect(Collectors.toList());
    }

    public Optional<PluginCapability> find(String pluginId, String key) {
        return listAll().stream()
                .filter(item -> Objects.equals(pluginId, item.getPluginId()))
                .filter(item -> Objects.equals(key, item.getKey()))
                .findFirst();
    }

    public boolean isAmbiguous(String key) {
        return listAll().stream()
                .filter(item -> Objects.equals(key, item.getKey()))
                .map(PluginCapability::getPluginId)
                .filter(Objects::nonNull)
                .distinct()
                .count() > 1;
    }

    public CapabilityDocument loadDocument() {
        return parseDocument(kvStore.get(KEY));
    }

    public CapabilityDocumentSnapshot loadSnapshot() {
        Optional<String> raw = kvStore.get(KEY);
        return new CapabilityDocumentSnapshot(raw, parseDocument(raw));
    }

    private CapabilityDocument parseDocument(Optional<String> json) {
        if (!json.isPresent() || json.get().trim().isEmpty()) {
            CapabilityDocument document = new CapabilityDocument();
            document.setUpdatedAt(RuntimeDates.nowString());
            return document;
        }
        CapabilityDocument document = gson.fromJson(json.get(), CapabilityDocument.class);
        if (document.getItems() == null) {
            document.setItems(new ArrayList<>());
        }
        return document;
    }

    public void saveDocument(CapabilityDocument document) {
        kvStore.put(KEY, documentJson(document));
    }

    public boolean saveDocumentIfUnchanged(CapabilityDocumentSnapshot snapshot) {
        String json = documentJson(snapshot.getDocument());
        if (kvStore instanceof ConditionalKvRepository) {
            return ((ConditionalKvRepository) kvStore).compareAndSet(KEY, snapshot.getRawJson(), json);
        }
        Optional<String> current = kvStore.get(KEY);
        if (!Objects.equals(current, snapshot.getRawJson())) {
            return false;
        }
        kvStore.put(KEY, json);
        return true;
    }

    private String documentJson(CapabilityDocument document) {
        document.setSchema(KEY);
        document.setVersion(1);
        document.setUpdatedAt(RuntimeDates.nowString());
        return gson.toJson(document);
    }

    public static boolean canGenerateLegacyCapability(String serviceName) {
        return serviceName != null && DOTTED_KEY_PATTERN.matcher(serviceName).matches();
    }

    public static Optional<PluginCapability> fromLegacyService(String pluginId, String pluginName, String serviceName) {
        if (!canGenerateLegacyCapability(serviceName)) {
            return Optional.empty();
        }
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginName);
        capability.setKey(serviceName);
        capability.setServiceName(serviceName);
        capability.setType("service");
        capability.setLabel(serviceName);
        capability.setExposure(Arrays.asList("internal"));
        capability.setRiskLevel("low");
        capability.setReadOnly(Boolean.FALSE);
        capability.setRequiresConfirmation(Boolean.FALSE);
        capability.setTimeoutSeconds(30);
        capability.setConcurrency(1);
        capability.setEnabled(Boolean.TRUE);
        capability.setLegacy(Boolean.TRUE);
        capability.setGenerated(Boolean.TRUE);
        return Optional.of(capability);
    }

    private void normalize(PluginCapability capability) {
        if (capability.getExposure() == null) {
            capability.setExposure(new ArrayList<>());
        }
        if (capability.getEnabled() == null) {
            capability.setEnabled(Boolean.TRUE);
        }
        if (capability.getRiskLevel() == null) {
            capability.setRiskLevel("low");
        }
    }

    private boolean sameIdentity(PluginCapability left, PluginCapability right) {
        return Objects.equals(left.getPluginId(), right.getPluginId()) && Objects.equals(left.getKey(), right.getKey());
    }

    public static class CapabilityDocumentSnapshot {
        private final Optional<String> rawJson;
        private final CapabilityDocument document;

        public CapabilityDocumentSnapshot(Optional<String> rawJson, CapabilityDocument document) {
            this.rawJson = rawJson;
            this.document = document;
        }

        public Optional<String> getRawJson() {
            return rawJson;
        }

        public CapabilityDocument getDocument() {
            return document;
        }
    }
}
