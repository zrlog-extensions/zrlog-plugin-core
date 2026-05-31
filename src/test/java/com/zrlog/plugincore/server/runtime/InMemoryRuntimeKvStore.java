package com.zrlog.plugincore.server.runtime;

import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.store.ConditionalKvRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryRuntimeKvStore implements KvRepository, ConditionalKvRepository {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Integer> getCounts = new HashMap<>();
    private final Map<String, Integer> putCounts = new HashMap<>();

    @Override
    public Optional<String> get(String key) {
        getCounts.put(key, getCount(key) + 1);
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value) {
        putCounts.put(key, putCount(key) + 1);
        values.put(key, value);
    }

    @Override
    public synchronized boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
        Optional<String> currentValue = Optional.ofNullable(values.get(key));
        if (!currentValue.equals(expectedValue)) {
            return false;
        }
        put(key, value);
        return true;
    }

    public int getCount(String key) {
        return getCounts.getOrDefault(key, 0);
    }

    public int putCount(String key) {
        return putCounts.getOrDefault(key, 0);
    }

    public void resetCounts() {
        getCounts.clear();
        putCounts.clear();
    }
}
