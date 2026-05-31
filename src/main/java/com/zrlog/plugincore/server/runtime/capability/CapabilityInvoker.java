package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugin.message.CapabilityInvokeResult;

import java.util.Map;

public interface CapabilityInvoker {

    CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context);
}
