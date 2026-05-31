package com.zrlog.plugincore.server.runtime.scheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class BearerSchedulerAuth {

    public boolean verify(List<SchedulerProviderSetting> providers, String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        if (token == null || token.isEmpty() || providers == null) {
            return false;
        }
        for (SchedulerProviderSetting provider : providers) {
            if (!Boolean.TRUE.equals(provider.getEnabled()) || isBlank(provider.getSecret())) {
                continue;
            }
            if (secureEquals(provider.getSecret(), token)) {
                return true;
            }
        }
        return false;
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String value = authorizationHeader.trim();
        int separator = value.indexOf(' ');
        if (separator <= 0) {
            return null;
        }
        String scheme = value.substring(0, separator);
        if (!"Bearer".equalsIgnoreCase(scheme)) {
            return null;
        }
        return value.substring(separator + 1).trim();
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
