package com.zrlog.plugincore.server.runtime.scheduler;

public class SchedulerExternalEndpoint {

    public static final String EXTERNAL_TICK_PATH = "/api/plugin/open/scheduler/tick";

    private SchedulerExternalEndpoint() {
    }

    public static String effectiveHost(String configuredHost, String fallbackHomeUrl) {
        String host = normalizeHost(configuredHost);
        if (!host.isEmpty()) {
            return host;
        }
        return normalizeHost(fallbackHomeUrl);
    }

    public static String tickUrl(String configuredHost, String fallbackHomeUrl) {
        String host = effectiveHost(configuredHost, fallbackHomeUrl);
        if (host.isEmpty()) {
            return EXTERNAL_TICK_PATH;
        }
        return host + EXTERNAL_TICK_PATH;
    }

    public static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.equalsIgnoreCase("http:") || normalized.equalsIgnoreCase("https:")) {
            return "";
        }
        if (normalized.startsWith("http:/") && !normalized.startsWith("http://")) {
            normalized = "http://" + normalized.substring("http:/".length());
        }
        if (normalized.startsWith("https:/") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized.substring("https:/".length());
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            if (normalized.endsWith("://")) {
                return "";
            }
            return normalized;
        }
        if (normalized.contains("://")) {
            return "";
        }
        return "https://" + normalized;
    }
}
