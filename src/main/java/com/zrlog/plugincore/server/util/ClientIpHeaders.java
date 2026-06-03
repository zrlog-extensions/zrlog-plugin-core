package com.zrlog.plugincore.server.util;

import com.hibegin.http.server.api.HttpRequest;

import java.util.HashMap;
import java.util.Map;

public class ClientIpHeaders {

    public static final String REAL_IP_HEADER = "X-Real-IP";

    private static final String[] REAL_IP_SOURCE_HEADERS = new String[]{
            "clientip",
            "CF-Connecting-IP",
            "X-Forwarded-For",
            "X-forwarded-for",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    private ClientIpHeaders() {
    }

    public static void putRealIpHeader(Map<String, String> headers, HttpRequest request) {
        if (headers == null || request == null) {
            return;
        }
        String realIp = realIp(headers, request.getRemoteHost());
        if (isBlank(realIp)) {
            return;
        }
        headers.put(REAL_IP_HEADER, realIp);
    }

    public static Map<String, String> copyHeadersWithRealIp(HttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        if (request != null && request.getHeaderMap() != null) {
            headers.putAll(request.getHeaderMap());
        }
        putRealIpHeader(headers, request);
        return headers;
    }

    static String realIp(Map<String, String> headers, String remoteHost) {
        if (headers != null) {
            for (String headerName : REAL_IP_SOURCE_HEADERS) {
                String value = headerValue(headers, headerName);
                if (!isBlank(value) && !"unknown".equalsIgnoreCase(value.trim())) {
                    return firstIp(value);
                }
            }
        }
        return firstIp(remoteHost);
    }

    private static String headerValue(Map<String, String> headers, String key) {
        String value = headers.get(key);
        if (!isBlank(value)) {
            return value;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey != null && entryKey.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstIp(String value) {
        if (value == null) {
            return null;
        }
        int commaIndex = value.indexOf(',');
        if (commaIndex >= 0) {
            return value.substring(0, commaIndex).trim();
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
