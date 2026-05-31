package com.zrlog.plugincore.server.runtime.state;

final class PluginRuntimeLeases {

    static final long LEASE_TTL_MS = 10 * 60 * 1000L;
    static final long LEGACY_INSTANCE_TTL_MS = 10 * 60 * 1000L;

    private PluginRuntimeLeases() {
    }

    static void renew(PluginRuntimeInstanceState instance, long nowMs) {
        instance.setHeartbeatAt(nowMs);
        instance.setLeaseExpiresAt(nowMs + LEASE_TTL_MS);
    }

    static boolean isExpired(PluginRuntimeInstanceState instance, long nowMs, long legacyTtlMs) {
        Long leaseExpiresAt = instance.getLeaseExpiresAt();
        if (leaseExpiresAt != null) {
            return nowMs >= leaseExpiresAt;
        }
        Long activeAt = instance.getLastActiveAt();
        if (activeAt == null) {
            activeAt = instance.getHeartbeatAt();
        }
        if (activeAt == null) {
            activeAt = instance.getReadyAt();
        }
        if (activeAt == null) {
            activeAt = instance.getStartedAt();
        }
        return activeAt != null && nowMs - activeAt >= legacyTtlMs;
    }
}
