package com.zrlog.plugincore.server.runtime.notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationDeliveryDocument {

    private String schema = NotificationDeliveryStore.KEY;
    private Integer version = 2;
    private String updatedAt;
    private List<NotificationDelivery> items = new ArrayList<>();

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

    public List<NotificationDelivery> getItems() {
        return items;
    }

    public void setItems(List<NotificationDelivery> items) {
        this.items = items;
    }
}
