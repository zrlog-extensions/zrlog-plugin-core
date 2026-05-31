package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.invocation.CapabilityInvocationLog;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogStore;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.util.RuntimeDates;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TrackingCapabilityInvoker implements CapabilityInvoker {

    private final CapabilityInvoker delegate;
    private final PluginRuntimeStateService stateService;
    private final InvocationLogStore invocationLogStore;
    private final CapabilityStore capabilityStore;

    public TrackingCapabilityInvoker(CapabilityInvoker delegate,
                                     PluginRuntimeStateService stateService,
                                     InvocationLogStore invocationLogStore) {
        this(delegate, stateService, invocationLogStore, null);
    }

    public TrackingCapabilityInvoker(CapabilityInvoker delegate,
                                     PluginRuntimeStateService stateService,
                                     InvocationLogStore invocationLogStore,
                                     CapabilityStore capabilityStore) {
        this.delegate = delegate;
        this.stateService = stateService;
        this.invocationLogStore = invocationLogStore;
        this.capabilityStore = capabilityStore;
    }

    @Override
    public CapabilityInvokeResult invoke(String pluginId, String capabilityKey, Map<String, Object> payload, InvokeContext context) {
        if (context == null) {
            context = new InvokeContext();
        }
        if (context.getRequestId() == null || context.getRequestId().trim().isEmpty()) {
            context.setRequestId(UUID.randomUUID().toString());
        }
        long startedAtMs = System.currentTimeMillis();
        String startedAt = RuntimeDates.nowString();
        CapabilityInvokeResult result;
        String validationError = validationError(pluginId, capabilityKey, context);
        if (validationError != null) {
            result = error(validationError);
            appendLog(pluginId, capabilityKey, context, result, startedAt, startedAtMs);
            return result;
        }
        Optional<PluginCapability> capability = capability(pluginId, capabilityKey);
        String pluginName = capability.map(PluginCapability::getPluginName).orElse(null);
        if (!stateService.ensureStarted(pluginId)) {
            result = error("Plugin start failed");
            appendLog(pluginId, capabilityKey, context, result, startedAt, startedAtMs);
            return result;
        }
        stateService.markInvocationStart(pluginId, pluginName);
        try {
            result = delegate.invoke(pluginId, capabilityKey, payload, context);
        } catch (RuntimeException e) {
            result = error(e.getMessage());
        }
        stateService.markInvocationEnd(pluginId, pluginName, result.isSuccess() ? null : result.getErrorMessage());
        appendLog(pluginId, capabilityKey, context, result, startedAt, startedAtMs);
        return result;
    }

    private String validationError(String pluginId, String capabilityKey, InvokeContext context) {
        if (capabilityStore == null) {
            return null;
        }
        if (isBlank(pluginId)) {
            return "Plugin id is empty";
        }
        if (isBlank(capabilityKey)) {
            return "Capability key is empty";
        }
        if (context == null || isBlank(context.getSource())) {
            return "Capability invoke source is empty";
        }
        Optional<PluginCapability> capability = capability(pluginId, capabilityKey);
        if (!capability.isPresent()) {
            return "Capability not found";
        }
        if (Boolean.FALSE.equals(capability.get().getEnabled())) {
            return "Capability is disabled";
        }
        if (!isExposedTo(capability.get(), context.getSource())) {
            return "Capability is not exposed to " + context.getSource();
        }
        return null;
    }

    private Optional<PluginCapability> capability(String pluginId, String capabilityKey) {
        if (capabilityStore == null) {
            return Optional.empty();
        }
        return capabilityStore.find(pluginId, capabilityKey);
    }

    private boolean isExposedTo(PluginCapability capability, String source) {
        List<String> exposure = capability.getExposure();
        return exposure != null && exposure.contains(source);
    }

    private void appendLog(String pluginId,
                           String capabilityKey,
                           InvokeContext context,
                           CapabilityInvokeResult result,
                           String startedAt,
                           long startedAtMs) {
        CapabilityInvocationLog log = new CapabilityInvocationLog();
        log.setId(UUID.randomUUID().toString());
        log.setPluginId(pluginId);
        log.setCapabilityKey(capabilityKey);
        log.setSource(context.getSource());
        log.setRequestId(context.getRequestId());
        log.setTraceId(context.getTraceId());
        log.setStartedAt(startedAt);
        log.setFinishedAt(RuntimeDates.nowString());
        log.setDurationMs(System.currentTimeMillis() - startedAtMs);
        log.setStatus(result.isSuccess() ? "success" : "error");
        log.setErrorMessage(result.getErrorMessage());
        invocationLogStore.append(log);
    }

    private CapabilityInvokeResult error(String message) {
        CapabilityInvokeResult result = new CapabilityInvokeResult();
        result.setSuccess(false);
        result.setErrorMessage(message == null || message.trim().isEmpty() ? "Capability invoke failed" : message);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
