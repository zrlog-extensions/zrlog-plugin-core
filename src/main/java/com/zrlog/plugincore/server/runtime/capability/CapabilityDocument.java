package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugin.message.PluginCapability;

import java.util.ArrayList;
import java.util.List;

public class CapabilityDocument {

    private String schema = CapabilityStore.KEY;
    private Integer version = 1;
    private String updatedAt;
    private List<PluginCapability> items = new ArrayList<>();

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PluginCapability> getItems() {
        return items;
    }

    public void setItems(List<PluginCapability> items) {
        this.items = items;
    }
}
