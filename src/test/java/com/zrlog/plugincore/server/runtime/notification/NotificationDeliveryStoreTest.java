package com.zrlog.plugincore.server.runtime.notification;

import com.google.gson.Gson;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NotificationDeliveryStoreTest {

    @Test
    public void shouldRetryNotificationDeliveryAppendWhenConcurrentWriteWinsFirst() {
        StaleNotificationDeliveryKvStore kvStore = new StaleNotificationDeliveryKvStore(documentJson(delivery("external")));
        NotificationDeliveryStore store = new NotificationDeliveryStore(kvStore);

        store.append(delivery("local"));

        assertEquals(2, store.list().size());
        assertTrue(store.list().stream().anyMatch(item -> "external".equals(item.getId())));
        assertTrue(store.list().stream().anyMatch(item -> "local".equals(item.getId())));
        assertEquals(2, kvStore.getNotificationDeliveryCompareAndSetCount());
    }

    private NotificationDelivery delivery(String id) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(id);
        return delivery;
    }

    private String documentJson(NotificationDelivery... deliveries) {
        NotificationDeliveryDocument document = new NotificationDeliveryDocument();
        document.setItems(Arrays.asList(deliveries));
        return new Gson().toJson(document);
    }

    private static class StaleNotificationDeliveryKvStore extends InMemoryRuntimeKvStore {
        private final String staleValue;
        private boolean staleInjected;
        private int notificationDeliveryCompareAndSetCount;

        private StaleNotificationDeliveryKvStore(String staleValue) {
            this.staleValue = staleValue;
        }

        @Override
        public synchronized boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
            if (!NotificationDeliveryStore.KEY.equals(key)) {
                return super.compareAndSet(key, expectedValue, value);
            }
            notificationDeliveryCompareAndSetCount++;
            if (!staleInjected) {
                put(key, staleValue);
                staleInjected = true;
                return false;
            }
            return super.compareAndSet(key, expectedValue, value);
        }

        private int getNotificationDeliveryCompareAndSetCount() {
            return notificationDeliveryCompareAndSetCount;
        }
    }
}
