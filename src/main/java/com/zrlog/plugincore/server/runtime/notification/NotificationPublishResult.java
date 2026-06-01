package com.zrlog.plugincore.server.runtime.notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationPublishResult {

    private int successCount;
    private int failedCount;
    private List<NotificationDelivery> deliveries = new ArrayList<NotificationDelivery>();

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<NotificationDelivery> getDeliveries() {
        return deliveries;
    }

    public void setDeliveries(List<NotificationDelivery> deliveries) {
        this.deliveries = deliveries;
    }

    public void success() {
        successCount++;
    }

    public void failed() {
        failedCount++;
    }

    public void addDelivery(NotificationDelivery delivery) {
        if (deliveries == null) {
            deliveries = new ArrayList<NotificationDelivery>();
        }
        deliveries.add(delivery);
    }
}
