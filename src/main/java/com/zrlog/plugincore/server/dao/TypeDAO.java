package com.zrlog.plugincore.server.dao;

import com.hibegin.common.dao.DAO;

import java.sql.SQLException;

public class TypeDAO extends DAO {

    public TypeDAO() {
        this.tableName = "type";
    }

    public Object findByName(String type) {
        try {
            return queryFirstObj("select typeId from type where typeName = ? ", type);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
