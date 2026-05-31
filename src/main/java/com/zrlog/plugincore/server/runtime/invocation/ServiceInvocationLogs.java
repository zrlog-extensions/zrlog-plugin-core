package com.zrlog.plugincore.server.runtime.invocation;

import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugincore.server.runtime.capability.RuntimeSources;

import java.util.UUID;

public final class ServiceInvocationLogs {

    private ServiceInvocationLogs() {
    }

    public static void append(KvRepository kvStore,
                              String pluginId,
                              String capabilityKey,
                              String requestId,
                              String traceId,
                              long startedAtMs,
                              long finishedAtMs,
                              String errorMessage) {
        new InvocationLogStore(kvStore).append(create(pluginId, capabilityKey, requestId, traceId, startedAtMs, finishedAtMs, errorMessage));
    }

    public static CapabilityInvocationLog create(String pluginId,
                                                 String capabilityKey,
                                                 String requestId,
                                                 String traceId,
                                                 long startedAtMs,
                                                 long finishedAtMs,
                                                 String errorMessage) {
        CapabilityInvocationLog log = new CapabilityInvocationLog();
        log.setId(UUID.randomUUID().toString());
        log.setPluginId(pluginId);
        log.setCapabilityKey(capabilityKey);
        log.setSource(RuntimeSources.INTERNAL);
        log.setRequestId(requestId);
        log.setTraceId(traceId);
        log.setStartedAt(startedAtMs);
        log.setFinishedAt(finishedAtMs);
        log.setDurationMs(finishedAtMs - startedAtMs);
        log.setStatus(isBlank(errorMessage) ? "success" : "error");
        log.setErrorMessage(isBlank(errorMessage) ? null : errorMessage);
        return log;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
