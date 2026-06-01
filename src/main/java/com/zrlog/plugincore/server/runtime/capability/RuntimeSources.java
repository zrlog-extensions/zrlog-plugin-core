package com.zrlog.plugincore.server.runtime.capability;

import java.util.List;
import java.util.Objects;

public final class RuntimeSources {

    public static final String SCHEDULER = "scheduler";
    public static final String TICK = "tick";
    public static final String INTERNAL = "internal";
    public static final String NOTIFICATION = "notification";
    public static final String RUNTIME_EVENT = "runtime_event";
    public static final String ADMIN_UI = "admin_ui";
    public static final String MCP = "mcp";

    private RuntimeSources() {
    }

    public static boolean isExposedTo(List<String> exposure, String source) {
        if (exposure == null || source == null) {
            return false;
        }
        if (exposure.contains(source)) {
            return true;
        }
        return Objects.equals(TICK, source) && exposure.contains(SCHEDULER);
    }
}
