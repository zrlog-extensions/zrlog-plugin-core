package com.zrlog.plugincore.server.runtime.state;

import java.util.ArrayList;
import java.util.List;

public class PluginRuntimeStateDocument {

    private String schema = PluginRuntimeStateStore.KEY;
    private int version = 1;
    private String updatedAt;
    private List<PluginRuntimeState> items = new ArrayList<PluginRuntimeState>();

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PluginRuntimeState> getItems() {
        return items;
    }

    public void setItems(List<PluginRuntimeState> items) {
        this.items = items;
    }
}
