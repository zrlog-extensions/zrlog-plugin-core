package com.zrlog.plugincore.server.runtime.state;

import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class PluginRuntimeInstances {

    private static final String INSTANCE_ID_PROPERTY = "zrlog.plugin.runtime.instanceId";
    private static final String INSTANCE_ID_ENV = "ZRLOG_PLUGIN_RUNTIME_INSTANCE_ID";
    private static final String HOST_NAME_PROPERTY = "zrlog.plugin.runtime.hostName";
    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong();
    private static final String HOST_NAME = resolveHostName();
    private static final String INSTANCE_ID = resolveInstanceId();

    private PluginRuntimeInstances() {
    }

    static String currentInstanceId() {
        return INSTANCE_ID;
    }

    static String newInstanceId() {
        return INSTANCE_ID + "/session-" + SESSION_SEQUENCE.incrementAndGet();
    }

    static String newProcessInstanceId(long processId) {
        return hostProcessId(HOST_NAME, processId);
    }

    private static String resolveInstanceId() {
        String configured = System.getProperty(INSTANCE_ID_PROPERTY);
        if (!isBlank(configured)) {
            return configured.trim();
        }
        configured = System.getenv(INSTANCE_ID_ENV);
        if (!isBlank(configured)) {
            return configured.trim();
        }
        return hostProcessId(HOST_NAME, ProcessHandle.current().pid());
    }

    static String hostProcessId(String hostName, long processId) {
        return normalizeHostName(hostName) + "-" + processId;
    }

    static String normalizeHostName(String hostName) {
        if (isBlank(hostName)) {
            return "localhost";
        }
        String normalized = hostName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return isBlank(normalized) ? "localhost" : normalized;
    }

    private static String resolveHostName() {
        String configured = System.getProperty(HOST_NAME_PROPERTY);
        if (!isBlank(configured)) {
            return configured;
        }
        configured = System.getenv("HOSTNAME");
        if (!isBlank(configured)) {
            return configured;
        }
        configured = System.getenv("COMPUTERNAME");
        if (!isBlank(configured)) {
            return configured;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "localhost";
        }
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
