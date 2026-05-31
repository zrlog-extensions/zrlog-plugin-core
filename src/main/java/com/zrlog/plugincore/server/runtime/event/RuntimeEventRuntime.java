package com.zrlog.plugincore.server.runtime.event;

import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.runtime.capability.CapabilityInvoker;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.InvokeContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RuntimeEventRuntime {

    public static final String SOURCE = "runtime_event";

    private final CapabilityStore capabilityStore;
    private final RuntimeEventHandlerResolver handlerResolver;
    private final CapabilityInvoker capabilityInvoker;

    public RuntimeEventRuntime(CapabilityStore capabilityStore,
                               RuntimeEventHandlerResolver handlerResolver,
                               CapabilityInvoker capabilityInvoker) {
        this.capabilityStore = capabilityStore;
        this.handlerResolver = handlerResolver;
        this.capabilityInvoker = capabilityInvoker;
    }

    public RuntimeEventPublishResult publish(RuntimeEventRequest request) {
        RuntimeEventPublishResult result = new RuntimeEventPublishResult();
        if (request == null || request.getEventType() == null || request.getEventType().trim().isEmpty()) {
            result.setFailedCount(1);
            return result;
        }
        for (PluginCapability handler : handlerResolver.resolve(request, capabilityStore.listAll())) {
            CapabilityInvokeResult invokeResult = capabilityInvoker.invoke(
                    handler.getPluginId(), handler.getKey(), eventPayload(request), invokeContext(request));
            if (invokeResult.isSuccess()) {
                result.success(handler.getPluginId());
            } else {
                result.failed(handler.getPluginId());
            }
        }
        return result;
    }

    private InvokeContext invokeContext(RuntimeEventRequest request) {
        InvokeContext context = new InvokeContext();
        context.setSource(SOURCE);
        context.setRequestId(request.getRequestId() == null ? UUID.randomUUID().toString() : request.getRequestId());
        context.setTraceId(request.getTraceId());
        context.setAuditRequired(true);
        return context;
    }

    private Map<String, Object> eventPayload(RuntimeEventRequest request) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("eventType", request.getEventType());
        map.put("aliases", request.getAliases());
        map.put("source", request.getSource());
        map.put("requestId", request.getRequestId());
        map.put("traceId", request.getTraceId());
        map.put("payload", request.getPayload());
        return map;
    }
}
