package com.zrlog.plugincore.server.runtime.notification;

import com.google.gson.Gson;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NotificationDeliveryStore {

    public static final String KEY = "plugin.runtime.notificationDeliveries";
    private static final int MAX_ITEMS = 500;
    private static final int STORE_UPDATE_RETRIES = 3;

    private final KvRepository kvStore;
    private final Gson gson = new Gson();

    public NotificationDeliveryStore(KvRepository kvStore) {
        this.kvStore = kvStore;
    }

    public List<NotificationDelivery> list() {
        return loadDocument().getItems();
    }

    public void append(NotificationDelivery delivery) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            NotificationDeliveryDocumentSnapshot snapshot = loadSnapshot();
            snapshot.getDocument().setItems(appendItems(snapshot.getDocument().getItems(), delivery));
            if (saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
    }

    private List<NotificationDelivery> appendItems(List<NotificationDelivery> currentItems, NotificationDelivery delivery) {
        List<NotificationDelivery> items = new ArrayList<>(currentItems);
        items.add(delivery);
        while (items.size() > MAX_ITEMS) {
            items.remove(0);
        }
        return items;
    }

    public NotificationDeliveryDocument loadDocument() {
        return parseDocument(kvStore.get(KEY));
    }

    public NotificationDeliveryDocumentSnapshot loadSnapshot() {
        Optional<String> raw = kvStore.get(KEY);
        return new NotificationDeliveryDocumentSnapshot(raw, parseDocument(raw));
    }

    private NotificationDeliveryDocument parseDocument(Optional<String> json) {
        if (!json.isPresent() || json.get().trim().isEmpty()) {
            NotificationDeliveryDocument document = new NotificationDeliveryDocument();
            document.setUpdatedAt(RuntimeDates.nowString());
            return document;
        }
        NotificationDeliveryDocument document = gson.fromJson(json.get(), NotificationDeliveryDocument.class);
        if (document.getItems() == null) {
            document.setItems(new ArrayList<>());
        }
        return document;
    }

    public void saveDocument(NotificationDeliveryDocument document) {
        kvStore.put(KEY, documentJson(document));
    }

    public boolean saveDocumentIfUnchanged(NotificationDeliveryDocumentSnapshot snapshot) {
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

    private String documentJson(NotificationDeliveryDocument document) {
        document.setSchema(KEY);
        document.setVersion(1);
        document.setUpdatedAt(RuntimeDates.nowString());
        return gson.toJson(document);
    }

    public static class NotificationDeliveryDocumentSnapshot {
        private final Optional<String> rawJson;
        private final NotificationDeliveryDocument document;

        public NotificationDeliveryDocumentSnapshot(Optional<String> rawJson, NotificationDeliveryDocument document) {
            this.rawJson = rawJson;
            this.document = document;
        }

        public Optional<String> getRawJson() {
            return rawJson;
        }

        public NotificationDeliveryDocument getDocument() {
            return document;
        }
    }
}
