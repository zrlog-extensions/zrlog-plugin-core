package com.zrlog.plugincore.server.runtime.invocation;

import com.google.gson.Gson;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InvocationLogStoreTest {

    @Test
    public void shouldTrimOldInvocationLogs() {
        InvocationLogStore store = new InvocationLogStore(new InMemoryRuntimeKvStore());
        InvocationLogDocument document = new InvocationLogDocument();
        List<CapabilityInvocationLog> logs = new ArrayList<CapabilityInvocationLog>();
        for (int i = 0; i < 1000; i++) {
            logs.add(log("log-" + i));
        }
        document.setItems(logs);
        store.saveDocument(document);

        for (int i = 1000; i < 1005; i++) {
            store.append(log("log-" + i));
        }

        assertEquals(1000, store.list().size());
        assertEquals("log-5", store.list().get(0).getId());
    }

    @Test
    public void shouldRetryAppendWhenConcurrentInvocationLogWriteWinsFirst() {
        StaleInvocationLogKvStore kvStore = new StaleInvocationLogKvStore(documentJson(log("external")));
        InvocationLogStore store = new InvocationLogStore(kvStore);

        store.append(log("local"));

        assertEquals(2, store.list().size());
        assertTrue(store.list().stream().anyMatch(item -> "external".equals(item.getId())));
        assertTrue(store.list().stream().anyMatch(item -> "local".equals(item.getId())));
        assertEquals(2, kvStore.getInvocationLogCompareAndSetCount());
    }

    private CapabilityInvocationLog log(String id) {
        CapabilityInvocationLog log = new CapabilityInvocationLog();
        log.setId(id);
        return log;
    }

    private String documentJson(CapabilityInvocationLog... logs) {
        InvocationLogDocument document = new InvocationLogDocument();
        document.setItems(Arrays.asList(logs));
        return new Gson().toJson(document);
    }

    private static class StaleInvocationLogKvStore extends InMemoryRuntimeKvStore {
        private final String staleValue;
        private boolean staleInjected;
        private int invocationLogCompareAndSetCount;

        private StaleInvocationLogKvStore(String staleValue) {
            this.staleValue = staleValue;
        }

        @Override
        public synchronized boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
            if (!InvocationLogStore.KEY.equals(key)) {
                return super.compareAndSet(key, expectedValue, value);
            }
            invocationLogCompareAndSetCount++;
            if (!staleInjected) {
                put(key, staleValue);
                staleInjected = true;
                return false;
            }
            return super.compareAndSet(key, expectedValue, value);
        }

        private int getInvocationLogCompareAndSetCount() {
            return invocationLogCompareAndSetCount;
        }
    }
}
