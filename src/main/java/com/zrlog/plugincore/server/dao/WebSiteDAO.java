package com.zrlog.plugincore.server.dao;


import com.hibegin.common.dao.DAO;
import com.zrlog.plugin.common.LoggerUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;

/**
 * Created by xiaochun on 2016/2/14.
 */
public class WebSiteDAO extends DAO {

    private static final Logger LOGGER = LoggerUtil.getLogger(WebSiteDAO.class);

    public WebSiteDAO() {
        this.tableName = "website";
    }

    public Object queryValueByName(String key) throws SQLException {
        DaoTrace.info(LOGGER, "website.queryValueByName", "key=" + key);
        return new WebSiteDAO().set("name", key).queryFirst("value");
    }

    public boolean saveOrUpdate(String key, Object value) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(key, value);
        return Boolean.TRUE.equals(saveOrUpdate(values).get(key));
    }

    public boolean compareAndSet(String key, String expectedValue, Object value) throws SQLException {
        DaoTrace.info(LOGGER, "website.compareAndSet",
                "key=" + key + " expected=" + DaoTrace.valueSummary(expectedValue)
                        + " value=" + DaoTrace.valueSummary(value));
        if (expectedValue == null) {
            if (exists(key)) {
                return false;
            }
            try {
                return new WebSiteDAO().set("name", key).set("value", value).save();
            } catch (SQLException e) {
                if (!isDuplicateKey(e)) {
                    throw e;
                }
                return false;
            }
        }
        Map<String, Object> cond = new HashMap<>();
        cond.put("name", key);
        cond.put("value", expectedValue);
        return new WebSiteDAO().set("value", value).update(cond);
    }

    public Map<String, Boolean> saveOrUpdate(Map<String, Object> values) throws SQLException {
        Map<String, Boolean> results = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return results;
        }
        DaoTrace.info(LOGGER, "website.saveOrUpdate", DaoTrace.keysSummary(values.keySet()));
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            results.put(entry.getKey(), saveOrUpdateOne(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    private boolean saveOrUpdateOne(String key, Object value) throws SQLException {
        Map<String, Object> cond = new HashMap<>();
        cond.put("name", key);
        if (new WebSiteDAO().set("value", value).update(cond)) {
            return true;
        }
        try {
            return new WebSiteDAO().set("name", key).set("value", value).save();
        } catch (SQLException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
            if (new WebSiteDAO().set("value", value).update(cond)) {
                return true;
            }
            return exists(key);
        }
    }

    private boolean exists(String key) throws SQLException {
        DaoTrace.info(LOGGER, "website.exists", "key=" + key);
        return new WebSiteDAO().queryFirstObj("select name from " + tableName + " where name=?", key) != null;
    }

    private boolean isDuplicateKey(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("duplicate")
                || lowerMessage.contains("unique")
                || lowerMessage.contains("constraint");
    }

    public Map<String, Object> getWebSiteByNameIn(List<String> names) throws SQLException {
        Map<String, Object> webSites = new HashMap<>();
        if (names == null || names.isEmpty()) {
            return webSites;
        }
        DaoTrace.info(LOGGER, "website.getWebSiteByNameIn", DaoTrace.keysSummary(names));

        StringJoiner sj = new StringJoiner(",");
        Object[] params = new String[names.size()];
        for (int i = 0; i < params.length; i++) {
            sj.add("?");
            params[i] = names.get(i);
        }
        List<Map<String, Object>> lw = queryListWithParams("select name,value from " + tableName + " where name in " + "(" + sj + ")", params);
        for (Map<String, Object> webSite : lw) {
            webSites.put((String) webSite.get("name"), webSite.get("value"));
        }
        return webSites;
    }
}
