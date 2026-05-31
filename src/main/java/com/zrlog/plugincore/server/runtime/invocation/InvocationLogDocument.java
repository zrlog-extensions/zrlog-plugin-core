package com.zrlog.plugincore.server.runtime.invocation;

import java.util.ArrayList;
import java.util.List;

public class InvocationLogDocument {

    private String schema = InvocationLogStore.KEY;
    private int version = 2;
    private String updatedAt;
    private List<CapabilityInvocationLog> items = new ArrayList<CapabilityInvocationLog>();

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

    public List<CapabilityInvocationLog> getItems() {
        return items;
    }

    public void setItems(List<CapabilityInvocationLog> items) {
        this.items = items;
    }
}
