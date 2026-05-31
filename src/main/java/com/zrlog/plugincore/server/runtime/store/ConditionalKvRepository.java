package com.zrlog.plugincore.server.runtime.store;

import com.zrlog.plugin.common.KvRepository;

import java.util.Optional;

public interface ConditionalKvRepository extends KvRepository {

    boolean compareAndSet(String key, Optional<String> expectedValue, String value);
}
