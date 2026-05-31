package com.zrlog.plugincore.server.runtime.state;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginIdleStopPolicyTest {

    private final PluginIdleStopPolicy policy = new PluginIdleStopPolicy();

    @Test
    public void shouldStopReadyProcessAfterIdleTimeout() {
        PluginRuntimeState state = state("ready", "process", 0, 1000L);

        assertTrue(policy.shouldStop(state, 7000L, 5000L));
    }

    @Test
    public void shouldStopReadyNativeProcessAfterIdleTimeout() {
        PluginRuntimeState state = state("ready", "native", 0, 1000L);

        assertTrue(policy.shouldStop(state, 7000L, 5000L));
    }

    @Test
    public void shouldKeepActiveOrNonStoppableRuntime() {
        assertFalse(policy.shouldStop(state("ready", "process", 1, 1000L), 7000L, 5000L));
        assertFalse(policy.shouldStop(state("ready", "agent", 0, 1000L), 7000L, 5000L));
    }

    @Test
    public void shouldTreatLegacyExecutingAsReadyLifecycle() {
        assertTrue(policy.shouldStop(state("executing", "process", 0, 1000L), 7000L, 5000L));
        assertFalse(policy.shouldStop(state("executing", "process", 1, 1000L), 7000L, 5000L));
    }

    @Test
    public void shouldKeepRecentlyActiveRuntime() {
        PluginRuntimeState state = state("ready", "process", 0, 4000L);

        assertFalse(policy.shouldStop(state, 7000L, 5000L));
    }

    private PluginRuntimeState state(String status, String runtimeMode, int activeCount, long lastActiveAt) {
        PluginRuntimeState state = new PluginRuntimeState();
        state.setStatus(status);
        state.setRuntimeMode(runtimeMode);
        state.setActiveInvocationCount(activeCount);
        state.setLastActiveAt(lastActiveAt);
        return state;
    }
}
