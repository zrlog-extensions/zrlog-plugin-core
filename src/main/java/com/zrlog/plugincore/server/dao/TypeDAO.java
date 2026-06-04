package com.zrlog.plugincore.server.dao;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.util.LoggerUtil;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TypeDAO extends DAO {

    private static final Logger LOGGER = LoggerUtil.getLogger(TypeDAO.class);

    public TypeDAO() {
        this.tableName = "type";
    }

    public Object findByName(String type) {
        try {
            return queryFirstObj("select typeId from type where typeName = ? ", type);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Find type by name failed: " + type, e);
        }
        return null;
    }
}
