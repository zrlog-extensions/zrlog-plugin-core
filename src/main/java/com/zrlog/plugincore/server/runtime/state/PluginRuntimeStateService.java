package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugincore.server.type.PluginStatus;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BiConsumer;

public class PluginRuntimeStateService {

    private static final long DEFAULT_START_WAIT_TIMEOUT_MS = 30000L;
    private static final long DEFAULT_START_WAIT_INTERVAL_MS = 100L;

    private final PluginRuntimeStateStore stateStore;
    private final PluginRuntimeStarter starter;
    private final long startWaitTimeoutMs;
    private final long startWaitIntervalMs;
    private final String runtimeInstanceId;

    public PluginRuntimeStateService(PluginRuntimeStateStore stateStore, PluginRuntimeStarter starter) {
        this(stateStore, starter, DEFAULT_START_WAIT_TIMEOUT_MS, DEFAULT_START_WAIT_INTERVAL_MS,
                PluginRuntimeInstances.currentInstanceId());
    }

    public PluginRuntimeStateService(PluginRuntimeStateStore stateStore,
                                     PluginRuntimeStarter starter,
                                     String runtimeInstanceId) {
        this(stateStore, starter, DEFAULT_START_WAIT_TIMEOUT_MS, DEFAULT_START_WAIT_INTERVAL_MS, runtimeInstanceId);
    }

    public PluginRuntimeStateService(PluginRuntimeStateStore stateStore,
                                     PluginRuntimeStarter starter,
                                     long startWaitTimeoutMs,
                                     long startWaitIntervalMs) {
        this(stateStore, starter, startWaitTimeoutMs, startWaitIntervalMs, PluginRuntimeInstances.currentInstanceId());
    }

    public PluginRuntimeStateService(PluginRuntimeStateStore stateStore,
                                     PluginRuntimeStarter starter,
                                     long startWaitTimeoutMs,
                                     long startWaitIntervalMs,
                                     String runtimeInstanceId) {
        this.stateStore = stateStore;
        this.starter = starter;
        this.startWaitTimeoutMs = startWaitTimeoutMs;
        this.startWaitIntervalMs = startWaitIntervalMs;
        this.runtimeInstanceId = runtimeInstanceId;
    }

    public boolean ensureStarted(String pluginId) {
        if (starter.isStarted(pluginId)) {
            return true;
        }
        Optional<PluginIdentity> identity = starter.findPlugin(pluginId);
        if (!identity.isPresent()) {
            markFailed(pluginId, null, "Plugin not registered");
            return false;
        }
        return ensureStarted(identity.get());
    }

    public boolean ensureStarted(PluginIdentity identity) {
        if (identity == null || isBlank(identity.getPluginId())) {
            return false;
        }
        if (starter.isStarted(identity.getPluginId())) {
            return true;
        }
        if (!starter.managesRuntimeState()) {
            markStarting(identity.getPluginId(), identity.getPluginName(), starter.runtimeMode(identity));
        }
        try {
            starter.start(identity);
        } catch (RuntimeException e) {
            if (starter.managesRuntimeState()) {
                starter.cleanupStartFailure(identity);
            } else {
                markFailed(identity.getPluginId(), identity.getPluginName(), e.getMessage());
            }
            return false;
        }
        long deadline = System.currentTimeMillis() + startWaitTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (starter.isStarted(identity.getPluginId())) {
                if (!starter.managesRuntimeState()) {
                    markReady(identity.getPluginId(), identity.getPluginName());
                }
                return true;
            }
            sleepQuietly();
        }
        if (!starter.managesRuntimeState()) {
            markFailed(identity.getPluginId(), identity.getPluginName(), "Plugin start timeout");
        } else {
            starter.cleanupStartFailure(identity);
        }
        return false;
    }

    public void markStarting(String pluginId, String pluginName, String runtimeMode) {
        markStarting(pluginId, pluginName, runtimeMode, null);
    }

    public void markStarting(String pluginId, String pluginName, String runtimeMode, Long processId) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.STARTING.runtimeStatus());
            instance.setRuntimeMode(runtimeMode);
            if (processId != null) {
                instance.setProcessId(processId);
            }
            instance.setStartedAt(now);
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            instance.setActiveInvocationCount(0);
            instance.setLastError(null);
        });
    }

    public void markInitializing(String pluginId, String pluginName, String runtimeMode) {
        markInitializing(pluginId, pluginName, runtimeMode, null);
    }

    public void markInitializing(String pluginId, String pluginName, String runtimeMode, Long processId) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.INITIALIZING.runtimeStatus());
            if (!isBlank(runtimeMode)) {
                instance.setRuntimeMode(runtimeMode);
            }
            if (processId != null) {
                instance.setProcessId(processId);
            }
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            instance.setLastError(null);
        });
    }

    public void markReady(String pluginId, String pluginName) {
        markReady(pluginId, pluginName, null);
    }

    public void markReady(String pluginId, String pluginName, Long processId) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.READY.runtimeStatus());
            if (processId != null) {
                instance.setProcessId(processId);
            }
            instance.setReadyAt(now);
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            instance.setActiveInvocationCount(0);
            instance.setLastError(null);
        });
    }

    public void markIdle(String pluginId, String pluginName) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.IDLE.runtimeStatus());
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            instance.setLastError(null);
        });
    }

    public void markStopping(String pluginId, String pluginName) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.STOPPING.runtimeStatus());
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
        });
    }

    public void markInvocationStart(String pluginId, String pluginName) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            normalizeInvocationLifecycleStatus(instance);
            instance.setActiveInvocationCount(activeCount(instance) + 1);
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
        });
    }

    public void markInvocationEnd(String pluginId, String pluginName, String errorMessage) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            int activeCount = Math.max(activeCount(instance) - 1, 0);
            instance.setActiveInvocationCount(activeCount);
            normalizeInvocationLifecycleStatus(instance);
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                instance.setLastError(errorMessage);
            }
        });
    }

    public void markFailed(String pluginId, String pluginName, String errorMessage) {
        update(pluginId, pluginName, (state, instance) -> {
            long now = now();
            instance.setStatus(PluginStatus.FAILED.runtimeStatus());
            instance.setLastActiveAt(now);
            PluginRuntimeLeases.renew(instance, now);
            instance.setActiveInvocationCount(0);
            instance.setLastError(errorMessage);
        });
    }

    public void markStopped(String pluginId, String pluginName) {
        update(pluginId, pluginName, (state, instance) -> {
            removeCurrentInstance(state);
            state.setStatus(PluginStatus.STOPPED.runtimeStatus());
            state.setActiveInvocationCount(0);
            state.setLastActiveAt(now());
        });
    }

    private void update(String pluginId, String pluginName, BiConsumer<PluginRuntimeState, PluginRuntimeInstanceState> consumer) {
        stateStore.update(pluginId, state -> {
            initializeState(state, pluginId, pluginName);
            PluginRuntimeInstanceState instance = currentInstance(state);
            initializeInstance(instance);
            consumer.accept(state, instance);
            PluginRuntimeStateAggregator.aggregate(state);
        });
    }

    private void initializeState(PluginRuntimeState state, String pluginId, String pluginName) {
        state.setPluginId(pluginId);
        if (pluginName != null && !pluginName.trim().isEmpty()) {
            state.setPluginName(pluginName);
        }
        if (state.getRuntimeMode() == null) {
            state.setRuntimeMode("process");
        }
        if (state.getActiveInvocationCount() == null) {
            state.setActiveInvocationCount(0);
        }
        if (state.getInstances() == null) {
            state.setInstances(new ArrayList<>());
        }
    }

    private PluginRuntimeInstanceState currentInstance(PluginRuntimeState state) {
        for (PluginRuntimeInstanceState instance : state.getInstances()) {
            if (Objects.equals(runtimeInstanceId, instance.getInstanceId())) {
                return instance;
            }
        }
        PluginRuntimeInstanceState instance = new PluginRuntimeInstanceState();
        instance.setInstanceId(runtimeInstanceId);
        state.getInstances().add(instance);
        return instance;
    }

    private void initializeInstance(PluginRuntimeInstanceState instance) {
        if (instance.getOwnerId() == null) {
            instance.setOwnerId(PluginRuntimeInstances.currentInstanceId());
        }
        if (instance.getRuntimeMode() == null) {
            instance.setRuntimeMode("process");
        }
        if (instance.getActiveInvocationCount() == null) {
            instance.setActiveInvocationCount(0);
        }
    }

    private int activeCount(PluginRuntimeInstanceState instance) {
        return instance.getActiveInvocationCount() == null ? 0 : instance.getActiveInvocationCount();
    }

    private void normalizeInvocationLifecycleStatus(PluginRuntimeInstanceState instance) {
        if (isBlank(instance.getStatus()) || PluginStatus.EXECUTING.runtimeStatus().equals(instance.getStatus())) {
            instance.setStatus(PluginStatus.READY.runtimeStatus());
        }
    }

    private void removeCurrentInstance(PluginRuntimeState state) {
        state.getInstances().removeIf(instance -> Objects.equals(runtimeInstanceId, instance.getInstanceId()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(startWaitIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
