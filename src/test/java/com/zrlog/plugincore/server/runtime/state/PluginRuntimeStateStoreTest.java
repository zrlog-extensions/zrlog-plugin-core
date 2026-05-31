package com.zrlog.plugincore.server.runtime.state;

import com.google.gson.Gson;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class PluginRuntimeStateStoreTest {

    @Test
    public void shouldCompactDuplicatePluginIdOnUpsert() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);
        PluginRuntimeStateDocument document = new PluginRuntimeStateDocument();
        document.setItems(Arrays.asList(
                state("plugin-a", "old"),
                state("plugin-a", "older"),
                state("plugin-b", "other")
        ));
        store.saveDocument(document);

        store.upsert(state("plugin-a", "current"));

        List<PluginRuntimeState> items = store.list();
        assertEquals(2, items.size());
        assertEquals("plugin-a", items.get(0).getPluginId());
        assertEquals("current", items.get(0).getPluginName());
        assertEquals("plugin-b", items.get(1).getPluginId());
    }

    @Test
    public void shouldDeletePluginStateById() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);
        store.upsert(state("plugin-a", "one"));
        store.upsert(state("plugin-b", "two"));

        store.delete("plugin-a");

        List<PluginRuntimeState> items = store.list();
        assertEquals(1, items.size());
        assertEquals("plugin-b", items.get(0).getPluginId());
    }

    @Test
    public void shouldRetryUpsertWhenConcurrentRuntimeStateWriteWinsFirst() {
        StaleOnceKvStore kvStore = new StaleOnceKvStore(Optional.<String>empty(), documentJson(state("plugin-b", "two")));
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);

        store.upsert(state("plugin-a", "one"));

        assertEquals(2, store.list().size());
        assertEquals("one", store.find("plugin-a").get().getPluginName());
        assertEquals("two", store.find("plugin-b").get().getPluginName());
        assertEquals(2, kvStore.getCompareAndSetCount());
    }

    @Test
    public void shouldRetryDeleteWhenConcurrentRuntimeStateWriteWinsFirst() {
        StaleOnceKvStore kvStore = new StaleOnceKvStore(
                Optional.of(documentJson(state("plugin-a", "one"))),
                documentJson(state("plugin-a", "one"), state("plugin-b", "two"))
        );
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);

        store.delete("plugin-a");

        List<PluginRuntimeState> items = store.list();
        assertEquals(1, items.size());
        assertEquals("plugin-b", items.get(0).getPluginId());
        assertEquals(2, kvStore.getCompareAndSetCount());
    }

    @Test
    public void shouldPruneInactiveInstancesAndReaggregateState() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);
        PluginRuntimeState state = state("reminder", "待办提醒");
        state.setInstances(Arrays.asList(
                instance("reminder-1", 1000L, 1),
                instance("reminder-2", 5000L, 2)
        ));
        store.upsert(state);

        store.pruneInactiveInstances(7000L, 3000L);

        PluginRuntimeState next = store.find("reminder").get();
        assertEquals(1, next.getInstances().size());
        assertEquals("reminder-2", next.getInstances().get(0).getInstanceId());
        assertEquals(Integer.valueOf(2), next.getActiveInvocationCount());
        assertEquals("ready", next.getStatus());
    }

    @Test
    public void shouldPruneStaleTransientInstances() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);
        PluginRuntimeState state = state("reminder", "待办提醒");
        state.setInstances(Arrays.asList(
                instance("starting-old", "starting", 1000L, 0),
                instance("initializing-old", "initializing", 2000L, 0),
                instance("ready", "ready", 1000L, 1),
                instance("starting-fresh", "starting", 6500L, 0)
        ));
        store.upsert(state);

        store.pruneStaleTransientInstances(7000L, 3000L);

        PluginRuntimeState next = store.find("reminder").get();
        assertEquals(2, next.getInstances().size());
        assertEquals("ready", next.getInstances().get(0).getInstanceId());
        assertEquals("starting-fresh", next.getInstances().get(1).getInstanceId());
        assertEquals(Integer.valueOf(1), next.getActiveInvocationCount());
        assertEquals("ready", next.getStatus());
    }

    private PluginRuntimeState state(String pluginId, String pluginName) {
        PluginRuntimeState state = new PluginRuntimeState();
        state.setPluginId(pluginId);
        state.setPluginName(pluginName);
        state.setStatus("ready");
        return state;
    }

    private PluginRuntimeInstanceState instance(String instanceId, long lastActiveAt, int activeCount) {
        return instance(instanceId, "ready", lastActiveAt, activeCount);
    }

    private PluginRuntimeInstanceState instance(String instanceId, String status, long lastActiveAt, int activeCount) {
        PluginRuntimeInstanceState instance = new PluginRuntimeInstanceState();
        instance.setInstanceId(instanceId);
        instance.setStatus(status);
        instance.setRuntimeMode("process");
        instance.setLastActiveAt(lastActiveAt);
        instance.setActiveInvocationCount(activeCount);
        return instance;
    }

    private String documentJson(PluginRuntimeState... states) {
        PluginRuntimeStateDocument document = new PluginRuntimeStateDocument();
        document.setItems(Arrays.asList(states));
        return new Gson().toJson(document);
    }

    private static class StaleOnceKvStore implements KvRepository, ConditionalKvRepository {
        private Optional<String> value;
        private final String staleValue;
        private boolean staleInjected;
        private int compareAndSetCount;

        private StaleOnceKvStore(Optional<String> value, String staleValue) {
            this.value = value;
            this.staleValue = staleValue;
        }

        @Override
        public Optional<String> get(String key) {
            return value;
        }

        @Override
        public void put(String key, String value) {
            this.value = Optional.ofNullable(value);
        }

        @Override
        public synchronized boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
            compareAndSetCount++;
            if (!staleInjected) {
                this.value = Optional.of(staleValue);
                staleInjected = true;
                return false;
            }
            if (!Objects.equals(this.value, expectedValue)) {
                return false;
            }
            this.value = Optional.ofNullable(value);
            return true;
        }

        private int getCompareAndSetCount() {
            return compareAndSetCount;
        }
    }
}
