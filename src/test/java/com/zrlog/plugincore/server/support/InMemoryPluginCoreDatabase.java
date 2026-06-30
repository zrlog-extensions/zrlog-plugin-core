package com.zrlog.plugincore.server.support;

import com.hibegin.common.dao.InMemoryDatabase;

import java.sql.SQLException;
import java.util.UUID;

public final class InMemoryPluginCoreDatabase implements AutoCloseable {

    private final InMemoryDatabase database;

    private InMemoryPluginCoreDatabase() throws SQLException {
        this.database = InMemoryDatabase.openH2("plugin_core_" + UUID.randomUUID());
        createSchema();
    }

    public static InMemoryPluginCoreDatabase open() throws SQLException {
        return new InMemoryPluginCoreDatabase();
    }

    private void createSchema() throws SQLException {
        database.update("create table website ("
                + "`name` varchar(191) not null primary key,"
                + "`value` longtext,"
                + "`remark` varchar(255)"
                + ")");
    }

    @Override
    public void close() {
        database.close();
    }
}
