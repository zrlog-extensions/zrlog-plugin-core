package com.zrlog.plugincore.server.runtime.store;

import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.dao.WebSiteDAO;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WebsiteRuntimeKvStore implements ConditionalKvRepository {

    @Override
    public Optional<String> get(String key) {
        try {
            Map<String, Object> values = new WebSiteDAO().getWebSiteByNameIn(Collections.singletonList(key));
            Object value = values.get(key);
            if (Objects.isNull(value)) {
                return Optional.empty();
            }
            return Optional.of(value.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void put(String key, String value) {
        try {
            new WebSiteDAO().saveOrUpdate(key, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
        try {
            return new WebSiteDAO().compareAndSet(key, expectedValue.orElse(null), value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
