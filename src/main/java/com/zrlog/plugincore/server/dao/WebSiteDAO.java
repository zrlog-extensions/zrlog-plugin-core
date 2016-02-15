package com.zrlog.plugincore.server.dao;


import com.hibegin.common.dao.DAO;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Created by xiaochun on 2016/2/14.
 */
public class WebSiteDAO extends DAO {
    public WebSiteDAO() {
        this.tableName = "website";
    }

    public boolean saveOrUpdate(String key, Object value) throws SQLException {
        Object object = new WebSiteDAO().set("name", key).queryFirst("value");
        if (object != null) {
            Map<String, Object> cond = new HashMap<>();
            cond.put("name", key);
            return new WebSiteDAO().set("value", value).update(cond);
        } else {
            return new WebSiteDAO().set("name", key).set("value", value).save();
        }
    }

    public Map<String, Object> getWebSiteByNameIn(List<String> names) throws SQLException {
        Map<String, Object> webSites = new HashMap<>();

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
