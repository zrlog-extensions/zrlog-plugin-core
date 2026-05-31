package com.zrlog.plugincore.server.type;

public enum PluginStatus {
    STOP("stopped", false, true),
    START("ready", true, false),
    WAIT_INSTALL("initializing", false, false),
    STARTING("starting", false, false),
    INITIALIZING("initializing", false, false),
    READY("ready", true, false),
    EXECUTING("executing", true, false),
    IDLE("idle", true, false),
    STOPPING("stopping", false, false),
    STOPPED("stopped", false, true),
    FAILED("failed", false, true);

    private final String runtimeStatus;
    private final boolean started;
    private final boolean terminal;

    PluginStatus(String runtimeStatus, boolean started, boolean terminal) {
        this.runtimeStatus = runtimeStatus;
        this.started = started;
        this.terminal = terminal;
    }

    public String runtimeStatus() {
        return runtimeStatus;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public static String lifecycleRuntimeStatus(String status) {
        if (EXECUTING.runtimeStatus.equals(status)) {
            return READY.runtimeStatus;
        }
        return fromRuntimeStatus(status).runtimeStatus();
    }

    public static PluginStatus fromRuntimeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return STOPPED;
        }
        for (PluginStatus value : values()) {
            if (value.runtimeStatus.equals(status)) {
                if (value == STOP || value == START || value == WAIT_INSTALL) {
                    continue;
                }
                return value;
            }
        }
        return STOPPED;
    }
}
