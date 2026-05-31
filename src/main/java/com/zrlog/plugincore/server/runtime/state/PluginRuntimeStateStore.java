package com.zrlog.plugincore.server.runtime.state;

import com.google.gson.Gson;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;
import com.zrlog.plugincore.server.type.PluginStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PluginRuntimeStateStore {

    public static final String KEY = "plugin.runtime.states";
    private static final int STORE_UPDATE_RETRIES = 3;

    private final KvRepository kvStore;
    private final Gson gson = new Gson();

    public PluginRuntimeStateStore(KvRepository kvStore) {
        this.kvStore = kvStore;
    }

    public List<PluginRuntimeState> list() {
        return loadDocument().getItems();
    }

    public Optional<PluginRuntimeState> find(String pluginId) {
        return list().stream()
                .filter(item -> Objects.equals(pluginId, item.getPluginId()))
                .findFirst();
    }

    public void update(String pluginId, Consumer<PluginRuntimeState> consumer) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            PluginRuntimeStateDocument document = snapshot.getDocument();
            PluginRuntimeState state = findItem(document.getItems(), pluginId).orElseGet(PluginRuntimeState::new);
            state.setPluginId(pluginId);
            consumer.accept(state);
            document.setItems(upsertItems(document.getItems(), state));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to update plugin runtime states due to concurrent modification");
    }

    public void upsert(PluginRuntimeState state) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            PluginRuntimeStateDocument document = snapshot.getDocument();
            document.setItems(upsertItems(document.getItems(), state));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to update plugin runtime states due to concurrent modification");
    }

    private List<PluginRuntimeState> upsertItems(List<PluginRuntimeState> currentItems, PluginRuntimeState state) {
        List<PluginRuntimeState> items = new ArrayList<PluginRuntimeState>();
        boolean replaced = false;
        Set<String> addedPluginIds = new HashSet<String>();
        for (PluginRuntimeState item : currentItems) {
            if (Objects.equals(item.getPluginId(), state.getPluginId())) {
                if (!replaced) {
                    items.add(state);
                    addedPluginIds.add(state.getPluginId());
                    replaced = true;
                }
            } else {
                if (addedPluginIds.add(item.getPluginId())) {
                    items.add(item);
                }
            }
        }
        if (!replaced) {
            items.add(state);
        }
        return items;
    }

    private Optional<PluginRuntimeState> findItem(List<PluginRuntimeState> items, String pluginId) {
        for (PluginRuntimeState item : items) {
            if (Objects.equals(pluginId, item.getPluginId())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public void delete(String pluginId) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            RemoveItemsResult result = removeItems(snapshot.getDocument().getItems(), pluginId);
            if (!result.isRemoved()) {
                return;
            }
            snapshot.getDocument().setItems(result.getItems());
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to delete plugin runtime state due to concurrent modification");
    }

    public void removeInstances(String pluginId, Predicate<PluginRuntimeInstanceState> predicate) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            Optional<PluginRuntimeState> optional = findItem(snapshot.getDocument().getItems(), pluginId);
            if (!optional.isPresent() || optional.get().getInstances() == null || optional.get().getInstances().isEmpty()) {
                return;
            }
            PluginRuntimeState state = optional.get();
            boolean removed = state.getInstances().removeIf(predicate);
            if (!removed) {
                return;
            }
            PluginRuntimeStateAggregator.aggregate(state);
            snapshot.getDocument().setItems(upsertItems(snapshot.getDocument().getItems(), state));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to remove plugin runtime instances due to concurrent modification");
    }

    public void pruneInactiveInstances(long nowMs, long inactiveTtlMs) {
        if (inactiveTtlMs <= 0) {
            return;
        }
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            boolean changed = false;
            for (PluginRuntimeState state : snapshot.getDocument().getItems()) {
                if (state.getInstances() == null || state.getInstances().isEmpty()) {
                    continue;
                }
                boolean removed = state.getInstances().removeIf(instance ->
                        isInactiveInstance(instance, nowMs, inactiveTtlMs));
                if (removed) {
                    PluginRuntimeStateAggregator.aggregate(state);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to prune inactive plugin runtime instances due to concurrent modification");
    }

    public void pruneExpiredLeases(long nowMs, long legacyTtlMs) {
        if (legacyTtlMs <= 0) {
            return;
        }
        pruneInstances(instance -> PluginRuntimeLeases.isExpired(instance, nowMs, legacyTtlMs),
                "Failed to prune expired plugin runtime instance leases due to concurrent modification");
    }

    public void pruneStaleTransientInstances(long nowMs, long transientTtlMs) {
        if (transientTtlMs <= 0) {
            return;
        }
        pruneInstances(instance -> isStaleTransientInstance(instance, nowMs, transientTtlMs),
                "Failed to prune stale transient plugin runtime instances due to concurrent modification");
    }

    private void pruneInstances(Predicate<PluginRuntimeInstanceState> predicate, String failureMessage) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            PluginRuntimeStateDocumentSnapshot snapshot = loadSnapshot();
            boolean changed = false;
            for (PluginRuntimeState state : snapshot.getDocument().getItems()) {
                if (state.getInstances() == null || state.getInstances().isEmpty()) {
                    continue;
                }
                boolean removed = state.getInstances().removeIf(predicate);
                if (removed) {
                    PluginRuntimeStateAggregator.aggregate(state);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException(failureMessage);
    }

    private RemoveItemsResult removeItems(List<PluginRuntimeState> currentItems, String pluginId) {
        List<PluginRuntimeState> items = new ArrayList<PluginRuntimeState>();
        boolean removed = false;
        for (PluginRuntimeState item : currentItems) {
            if (Objects.equals(pluginId, item.getPluginId())) {
                removed = true;
                continue;
            }
            items.add(item);
        }
        return new RemoveItemsResult(items, removed);
    }

    private boolean isInactiveInstance(PluginRuntimeInstanceState instance, long nowMs, long inactiveTtlMs) {
        Long activeAt = instance.getLastActiveAt();
        if (activeAt == null) {
            activeAt = instance.getReadyAt();
        }
        if (activeAt == null) {
            activeAt = instance.getStartedAt();
        }
        return activeAt != null && nowMs - activeAt >= inactiveTtlMs;
    }

    private boolean isStaleTransientInstance(PluginRuntimeInstanceState instance, long nowMs, long transientTtlMs) {
        PluginStatus status = PluginStatus.fromRuntimeStatus(instance.getStatus());
        if (status != PluginStatus.STARTING && status != PluginStatus.INITIALIZING) {
            return false;
        }
        Long activeAt = instance.getLastActiveAt();
        if (activeAt == null) {
            activeAt = instance.getStartedAt();
        }
        return activeAt != null && nowMs - activeAt >= transientTtlMs;
    }

    public PluginRuntimeStateDocument loadDocument() {
        return parseDocument(kvStore.get(KEY));
    }

    public PluginRuntimeStateDocumentSnapshot loadSnapshot() {
        Optional<String> raw = kvStore.get(KEY);
        return new PluginRuntimeStateDocumentSnapshot(raw, parseDocument(raw));
    }

    private PluginRuntimeStateDocument parseDocument(Optional<String> json) {
        if (!json.isPresent() || json.get().trim().isEmpty()) {
            PluginRuntimeStateDocument document = new PluginRuntimeStateDocument();
            document.setUpdatedAt(RuntimeDates.nowString());
            return document;
        }
        PluginRuntimeStateDocument document = gson.fromJson(json.get(), PluginRuntimeStateDocument.class);
        if (document.getItems() == null) {
            document.setItems(new ArrayList<PluginRuntimeState>());
        }
        return document;
    }

    public void saveDocument(PluginRuntimeStateDocument document) {
        kvStore.put(KEY, documentJson(document));
    }

    public boolean saveDocumentIfUnchanged(PluginRuntimeStateDocumentSnapshot snapshot) {
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

    private String documentJson(PluginRuntimeStateDocument document) {
        document.setSchema(KEY);
        document.setVersion(1);
        document.setUpdatedAt(RuntimeDates.nowString());
        for (PluginRuntimeState item : document.getItems()) {
            item.setEffectiveStatus(null);
        }
        return gson.toJson(document);
    }

    private static class RemoveItemsResult {
        private final List<PluginRuntimeState> items;
        private final boolean removed;

        private RemoveItemsResult(List<PluginRuntimeState> items, boolean removed) {
            this.items = items;
            this.removed = removed;
        }

        public List<PluginRuntimeState> getItems() {
            return items;
        }

        public boolean isRemoved() {
            return removed;
        }
    }

    public static class PluginRuntimeStateDocumentSnapshot {
        private final Optional<String> rawJson;
        private final PluginRuntimeStateDocument document;

        public PluginRuntimeStateDocumentSnapshot(Optional<String> rawJson, PluginRuntimeStateDocument document) {
            this.rawJson = rawJson;
            this.document = document;
        }

        public Optional<String> getRawJson() {
            return rawJson;
        }

        public PluginRuntimeStateDocument getDocument() {
            return document;
        }
    }
}
