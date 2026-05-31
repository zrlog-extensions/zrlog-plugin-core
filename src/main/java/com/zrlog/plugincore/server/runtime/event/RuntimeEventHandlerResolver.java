package com.zrlog.plugincore.server.runtime.event;

import com.zrlog.plugin.message.PluginCapability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class RuntimeEventHandlerResolver {

    public List<PluginCapability> resolve(RuntimeEventRequest request, List<PluginCapability> capabilities) {
        Set<String> eventTypes = eventTypes(request);
        if (eventTypes.isEmpty()) {
            return new ArrayList<>();
        }
        return capabilities.stream()
                .filter(item -> Objects.equals("event_handler", item.getType()))
                .filter(item -> item.getExposure() != null && item.getExposure().contains("runtime_event"))
                .filter(item -> subscribes(item, eventTypes))
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .sorted(Comparator.comparing(this::stableHandlerKey))
                .collect(Collectors.toList());
    }

    private boolean subscribes(PluginCapability capability, Set<String> eventTypes) {
        for (String item : subscribedEvents(capability)) {
            if (eventTypes.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> subscribedEvents(PluginCapability capability) {
        Set<String> events = new LinkedHashSet<>();
        if (capability.getChannel() == null) {
            return events;
        }
        String[] parts = capability.getChannel().split(",");
        for (String part : parts) {
            String value = normalize(part);
            if (value != null) {
                events.add(value);
            }
        }
        return events;
    }

    private Set<String> eventTypes(RuntimeEventRequest request) {
        Set<String> eventTypes = new LinkedHashSet<>();
        if (request == null) {
            return eventTypes;
        }
        String eventType = normalize(request.getEventType());
        if (eventType != null) {
            eventTypes.add(eventType);
        }
        if (request.getAliases() != null) {
            for (String alias : request.getAliases()) {
                String value = normalize(alias);
                if (value != null) {
                    eventTypes.add(value);
                }
            }
        }
        return eventTypes;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String stableHandlerKey(PluginCapability capability) {
        String name = capability.getPluginName() == null ? "" : capability.getPluginName();
        String id = capability.getPluginId() == null ? "" : capability.getPluginId();
        String key = capability.getKey() == null ? "" : capability.getKey();
        return name + ":" + id + ":" + key;
    }
}
