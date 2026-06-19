package com.zrlog.plugincore.server.runtime.plugin.log;

import com.hibegin.common.dao.DaoLogContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PluginLogContextTest {

    @Test
    public void openUsesShortNameOnlyForLogLabel() {
        try (PluginLogContext.Scope ignored = PluginLogContext.open("plugin-id", "backup-sql-file", "备份数据文件")) {
            assertEquals("backup-sql-file", PluginLogContext.currentLabel());
            assertEquals("backup-sql-file", DaoLogContext.currentLabel());
            assertEquals("[backup-sql-file] select 1", DaoLogContext.format("select 1"));
            assertEquals("[backup-sql-file] run plugin", PluginLogContext.prefix("run plugin"));
        }
        assertNull(PluginLogContext.currentLabel());
        assertNull(DaoLogContext.currentLabel());
    }

    @Test
    public void pluginIdOnlyScopeDoesNotOverrideExistingShortName() {
        try (PluginLogContext.Scope ignored = PluginLogContext.open("plugin-id", "backup-sql-file", "备份数据文件")) {
            try (PluginLogContext.Scope nested = PluginLogContext.open("other-plugin-id", null, null)) {
                assertEquals("backup-sql-file", PluginLogContext.currentLabel());
                assertEquals("backup-sql-file", DaoLogContext.currentLabel());
            }
            assertEquals("backup-sql-file", PluginLogContext.currentLabel());
            assertEquals("backup-sql-file", DaoLogContext.currentLabel());
        }
        assertNull(PluginLogContext.currentLabel());
        assertNull(DaoLogContext.currentLabel());
    }

    @Test
    public void pluginIdScopeUsesCachedShortNameInsteadOfDisplayName() {
        try (PluginLogContext.Scope ignored = PluginLogContext.open("site-check-id", "site-check", "站点检查")) {
            assertEquals("site-check", PluginLogContext.currentLabel());
        }

        try (PluginLogContext.Scope ignored = PluginLogContext.open("site-check-id", null, "站点检查")) {
            assertEquals("site-check", PluginLogContext.currentLabel());
            assertEquals("[site-check] select 1", DaoLogContext.format("select 1"));
        }
    }

    @Test
    public void displayNameOnlyDoesNotBecomeLogLabel() {
        try (PluginLogContext.Scope ignored = PluginLogContext.open(null, null, "站点检查")) {
            assertNull(PluginLogContext.currentLabel());
            assertNull(DaoLogContext.currentLabel());
            assertEquals("select 1", DaoLogContext.format("select 1"));
        }
    }
}
