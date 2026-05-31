package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugincore.server.type.PluginStatus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PluginIdleStopPolicy {

    private static final Set<String> STOPPABLE_STATUSES = new HashSet<String>(Arrays.asList(
            PluginStatus.READY.runtimeStatus(),
            PluginStatus.IDLE.runtimeStatus()
    ));
    private static final Set<String> STOPPABLE_RUNTIME_MODES = new HashSet<String>(Arrays.asList(
            "process",
            "native"
    ));

    public boolean shouldStop(PluginRuntimeState state, long nowMs, long idleTimeoutMs) {
        if (state == null || idleTimeoutMs <= 0) {
            return false;
        }
        if (!STOPPABLE_RUNTIME_MODES.contains(state.getRuntimeMode())) {
            return false;
        }
        if (!STOPPABLE_STATUSES.contains(PluginStatus.lifecycleRuntimeStatus(state.getStatus()))) {
            return false;
        }
        if (state.getActiveInvocationCount() != null && state.getActiveInvocationCount() > 0) {
            return false;
        }
        if (state.getLastActiveAt() == null) {
            return false;
        }
        return nowMs - state.getLastActiveAt() >= idleTimeoutMs;
    }
}
