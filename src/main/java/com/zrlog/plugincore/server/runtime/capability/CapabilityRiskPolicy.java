package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugin.message.PluginCapability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CapabilityRiskPolicy {

    public static final String LOW = "low";
    public static final String MEDIUM = "medium";
    public static final String HIGH = "high";
    public static final String CRITICAL = "critical";

    private static final List<String> RISK_LEVELS = Arrays.asList(LOW, MEDIUM, HIGH, CRITICAL);

    private CapabilityRiskPolicy() {
    }

    public static void normalize(PluginCapability capability) {
        if (capability == null) {
            return;
        }
        String riskLevel = normalizeRiskLevel(capability.getRiskLevel());
        capability.setRiskLevel(riskLevel);
        if (isHighOrCritical(riskLevel)) {
            capability.setExposure(withoutMcpExposure(capability.getExposure()));
        }
    }

    public static String invocationError(PluginCapability capability, InvokeContext context) {
        if (capability == null || context == null || context.getSource() == null) {
            return null;
        }
        String riskLevel;
        try {
            riskLevel = normalizeRiskLevel(capability.getRiskLevel());
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (Objects.equals(RuntimeSources.MCP, context.getSource()) && isHighOrCritical(riskLevel)) {
            return "High risk capability is not exposed to mcp";
        }
        return null;
    }

    public static boolean auditRequired(PluginCapability capability, InvokeContext context) {
        if (context != null && context.isAuditRequired()) {
            return true;
        }
        if (capability == null) {
            return false;
        }
        try {
            return isAtLeastMedium(capability.getRiskLevel()) || Boolean.TRUE.equals(capability.getRequiresConfirmation());
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static String riskLevel(PluginCapability capability) {
        if (capability == null) {
            return null;
        }
        try {
            return normalizeRiskLevel(capability.getRiskLevel());
        } catch (IllegalArgumentException e) {
            return capability.getRiskLevel();
        }
    }

    public static String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            return LOW;
        }
        String normalized = riskLevel.trim().toLowerCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported capability riskLevel: " + riskLevel);
        }
        return normalized;
    }

    private static List<String> withoutMcpExposure(List<String> exposure) {
        List<String> items = new ArrayList<String>();
        if (exposure == null) {
            return items;
        }
        for (String item : exposure) {
            if (!Objects.equals(RuntimeSources.MCP, item)) {
                items.add(item);
            }
        }
        return items;
    }

    private static boolean isAtLeastMedium(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return Objects.equals(MEDIUM, normalized) || isHighOrCritical(normalized);
    }

    private static boolean isHighOrCritical(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return Objects.equals(HIGH, normalized) || Objects.equals(CRITICAL, normalized);
    }
}
