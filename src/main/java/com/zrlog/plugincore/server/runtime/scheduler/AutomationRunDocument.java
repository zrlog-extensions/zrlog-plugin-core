package com.zrlog.plugincore.server.runtime.scheduler;

import java.util.ArrayList;
import java.util.List;

public class AutomationRunDocument {

    private String schema = AutomationRunStore.KEY;
    private Integer version = 1;
    private String updatedAt;
    private List<PluginAutomationRun> items = new ArrayList<>();

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

    public List<PluginAutomationRun> getItems() {
        return items;
    }

    public void setItems(List<PluginAutomationRun> items) {
        this.items = items;
    }
}
