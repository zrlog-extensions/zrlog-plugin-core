package com.zrlog.plugincore.server.runtime.util;

import java.time.Instant;

public class RuntimeDates {

    private RuntimeDates() {
    }

    public static String nowString() {
        return Instant.now().toString();
    }
}
