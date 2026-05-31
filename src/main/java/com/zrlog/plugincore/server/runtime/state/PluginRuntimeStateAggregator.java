package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugincore.server.type.PluginStatus;

import java.util.List;

final class PluginRuntimeStateAggregator {

    private PluginRuntimeStateAggregator() {
    }

    static void aggregate(PluginRuntimeState state) {
        List<PluginRuntimeInstanceState> instances = state.getInstances();
        if (instances == null || instances.isEmpty()) {
            state.setStatus(PluginStatus.STOPPED.runtimeStatus());
            state.setActiveInvocationCount(0);
            return;
        }
        int activeCount = 0;
        Long startedAt = null;
        Long readyAt = null;
        Long lastActiveAt = null;
        String runtimeMode = null;
        String lastError = null;
        PluginStatus lifecycleStatus = null;
        for (PluginRuntimeInstanceState instance : instances) {
            activeCount += activeCount(instance);
            startedAt = min(startedAt, instance.getStartedAt());
            readyAt = max(readyAt, instance.getReadyAt());
            lastActiveAt = max(lastActiveAt, instance.getLastActiveAt());
            if (isBlank(runtimeMode) && !isBlank(instance.getRuntimeMode())) {
                runtimeMode = instance.getRuntimeMode();
            }
            if (!isBlank(instance.getLastError())) {
                lastError = instance.getLastError();
            }
            lifecycleStatus = higherPriority(lifecycleStatus,
                    PluginStatus.fromRuntimeStatus(PluginStatus.lifecycleRuntimeStatus(instance.getStatus())));
        }
        state.setStatus(lifecycleStatus == null ? PluginStatus.STOPPED.runtimeStatus() : lifecycleStatus.runtimeStatus());
        state.setRuntimeMode(runtimeMode);
        state.setStartedAt(startedAt);
        state.setReadyAt(readyAt);
        state.setLastActiveAt(lastActiveAt);
        state.setActiveInvocationCount(activeCount);
        state.setLastError(lastError);
    }

    static String effectiveStatus(PluginRuntimeState state) {
        if (state.getActiveInvocationCount() != null && state.getActiveInvocationCount() > 0
                && PluginStatus.fromRuntimeStatus(state.getStatus()).isStarted()) {
            return PluginStatus.EXECUTING.runtimeStatus();
        }
        return state.getStatus();
    }

    private static int activeCount(PluginRuntimeInstanceState instance) {
        return instance.getActiveInvocationCount() == null ? 0 : instance.getActiveInvocationCount();
    }

    private static PluginStatus higherPriority(PluginStatus current, PluginStatus next) {
        if (next == null) {
            return current;
        }
        if (current == null || priority(next) > priority(current)) {
            return next;
        }
        return current;
    }

    private static int priority(PluginStatus status) {
        switch (status) {
            case READY:
            case IDLE:
                return 50;
            case INITIALIZING:
                return 40;
            case STARTING:
                return 30;
            case FAILED:
                return 20;
            case STOPPING:
                return 10;
            default:
                return 0;
        }
    }

    private static Long min(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    private static Long max(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
