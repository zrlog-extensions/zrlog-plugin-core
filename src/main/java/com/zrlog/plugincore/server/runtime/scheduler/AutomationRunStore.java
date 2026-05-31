package com.zrlog.plugincore.server.runtime.scheduler;

import com.google.gson.Gson;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AutomationRunStore {

    public static final String KEY = "plugin.runtime.automationRuns.v2";
    private static final int MAX_ITEMS = 500;
    private static final int STORE_UPDATE_RETRIES = 3;

    private final KvRepository kvStore;
    private final Gson gson = new Gson();

    public AutomationRunStore(KvRepository kvStore) {
        this.kvStore = kvStore;
    }

    public List<PluginAutomationRun> list() {
        return loadDocument().getItems();
    }

    public void append(PluginAutomationRun run) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            AutomationRunDocumentSnapshot snapshot = loadSnapshot();
            snapshot.getDocument().setItems(appendItems(snapshot.getDocument().getItems(), run));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
    }

    private List<PluginAutomationRun> appendItems(List<PluginAutomationRun> currentItems, PluginAutomationRun run) {
        List<PluginAutomationRun> items = new ArrayList<>(currentItems);
        items.add(run);
        while (items.size() > MAX_ITEMS) {
            items.remove(0);
        }
        return items;
    }

    public AutomationRunDocument loadDocument() {
        return parseDocument(kvStore.get(KEY));
    }

    public AutomationRunDocumentSnapshot loadSnapshot() {
        Optional<String> raw = kvStore.get(KEY);
        return new AutomationRunDocumentSnapshot(raw, parseDocument(raw));
    }

    private AutomationRunDocument parseDocument(Optional<String> json) {
        if (!json.isPresent() || json.get().trim().isEmpty()) {
            AutomationRunDocument document = new AutomationRunDocument();
            document.setUpdatedAt(RuntimeDates.nowString());
            return document;
        }
        AutomationRunDocument document = gson.fromJson(json.get(), AutomationRunDocument.class);
        if (document.getItems() == null) {
            document.setItems(new ArrayList<>());
        }
        return document;
    }

    public void saveDocument(AutomationRunDocument document) {
        kvStore.put(KEY, documentJson(document));
    }

    public boolean saveDocumentIfUnchanged(AutomationRunDocumentSnapshot snapshot) {
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

    private String documentJson(AutomationRunDocument document) {
        document.setSchema(KEY);
        document.setVersion(2);
        document.setUpdatedAt(RuntimeDates.nowString());
        return gson.toJson(document);
    }

    public static class AutomationRunDocumentSnapshot {
        private final Optional<String> rawJson;
        private final AutomationRunDocument document;

        public AutomationRunDocumentSnapshot(Optional<String> rawJson, AutomationRunDocument document) {
            this.rawJson = rawJson;
            this.document = document;
        }

        public Optional<String> getRawJson() {
            return rawJson;
        }

        public AutomationRunDocument getDocument() {
            return document;
        }
    }
}
