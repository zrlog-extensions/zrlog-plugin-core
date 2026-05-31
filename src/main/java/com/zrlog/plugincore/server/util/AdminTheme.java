package com.zrlog.plugincore.server.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.model.PublicInfo;
import com.zrlog.plugin.data.codec.BaseHttpRequestInfo;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminTheme {

    public static final String DARK_MODE_HEADER = BaseHttpRequestInfo.DARK_MODE_HEADER;
    public static final String ADMIN_COLOR_PRIMARY_HEADER = BaseHttpRequestInfo.ADMIN_COLOR_PRIMARY_HEADER;

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminTheme.class);

    private final boolean darkMode;
    private final String adminColorPrimary;

    public AdminTheme(boolean darkMode, String adminColorPrimary) {
        this.darkMode = darkMode;
        this.adminColorPrimary = normalizeAdminColorPrimary(adminColorPrimary);
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public String getAdminColorPrimary() {
        return adminColorPrimary;
    }

    public static AdminTheme fromRequest(HttpRequest request) {
        Map<String, String> headers = request == null ? null : request.getHeaderMap();
        PublicInfo fallback = null;
        if (missingThemeHeader(headers)) {
            fallback = loadPublicInfoSilently();
        }
        return fromHeaders(headers, fallback);
    }

    public static Map<String, String> copyHeadersWithFallback(HttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        if (request != null && request.getHeaderMap() != null) {
            headers.putAll(request.getHeaderMap());
        }
        AdminTheme theme = fromRequest(request);
        theme.putMissingHeaders(headers);
        return headers;
    }

    public static void applyTo(BaseHttpRequestInfo requestInfo, HttpRequest request) {
        if (requestInfo == null) {
            return;
        }
        AdminTheme theme = fromRequest(request);
        requestInfo.setHeader(copyHeadersWithFallback(request, theme));
        theme.applyTo(requestInfo);
    }

    public static AdminTheme fromHeaders(Map<String, String> headers, PublicInfo fallback) {
        String darkModeHeader = headerValue(headers, DARK_MODE_HEADER, "Dark_mode", "dark_mode");
        String adminColorPrimaryHeader = headerValue(headers, ADMIN_COLOR_PRIMARY_HEADER, "admin_color_Primary", "admin_color_primary");
        boolean darkMode = isBlank(darkModeHeader) ? publicInfoDarkMode(fallback) : BooleanUtils.isTrue(darkModeHeader);
        String adminColorPrimary = isBlank(adminColorPrimaryHeader) ? publicInfoAdminColorPrimary(fallback) : adminColorPrimaryHeader;
        return new AdminTheme(darkMode, adminColorPrimary);
    }

    public void putMissingHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        if (isBlank(headers.get(DARK_MODE_HEADER))) {
            headers.put(DARK_MODE_HEADER, darkMode + "");
        }
        if (isBlank(headers.get(ADMIN_COLOR_PRIMARY_HEADER))) {
            headers.put(ADMIN_COLOR_PRIMARY_HEADER, adminColorPrimary);
        }
    }

    public void applyTo(BaseHttpRequestInfo requestInfo) {
        if (requestInfo == null) {
            return;
        }
        requestInfo.setDarkMode(darkMode);
        requestInfo.setAdminColorPrimary(adminColorPrimary);
        Map<String, String> headers = requestInfo.getHeader();
        if (headers == null) {
            headers = new HashMap<>();
            requestInfo.setHeader(headers);
        }
        putMissingHeaders(headers);
    }

    private static Map<String, String> copyHeadersWithFallback(HttpRequest request, AdminTheme theme) {
        Map<String, String> headers = new HashMap<>();
        if (request != null && request.getHeaderMap() != null) {
            headers.putAll(request.getHeaderMap());
        }
        theme.putMissingHeaders(headers);
        return headers;
    }

    private static boolean missingThemeHeader(Map<String, String> headers) {
        return isBlank(headerValue(headers, DARK_MODE_HEADER, "Dark_mode", "dark_mode"))
                || isBlank(headerValue(headers, ADMIN_COLOR_PRIMARY_HEADER, "admin_color_Primary", "admin_color_primary"));
    }

    private static String headerValue(Map<String, String> headers, String key, String... aliases) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String value = headers.get(key);
        if (!isBlank(value)) {
            return value;
        }
        for (String alias : aliases) {
            value = headers.get(alias);
            if (!isBlank(value)) {
                return value;
            }
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey == null) {
                continue;
            }
            if (Objects.equals(entryKey, key) || entryKey.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
            for (String alias : aliases) {
                if (Objects.equals(entryKey, alias) || entryKey.equalsIgnoreCase(alias)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static PublicInfo loadPublicInfoSilently() {
        try {
            return PublicInfoLoader.loadPublicInfo();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "load public info for admin theme failed", e);
            return null;
        }
    }

    private static boolean publicInfoDarkMode(PublicInfo publicInfo) {
        return publicInfo != null && Boolean.TRUE.equals(publicInfo.getDarkMode());
    }

    private static String publicInfoAdminColorPrimary(PublicInfo publicInfo) {
        if (publicInfo == null) {
            return PublicInfoLoader.DEFAULT_ADMIN_COLOR_PRIMARY;
        }
        return normalizeAdminColorPrimary(publicInfo.getAdminColorPrimary());
    }

    private static String normalizeAdminColorPrimary(String adminColorPrimary) {
        if (isBlank(adminColorPrimary)) {
            return PublicInfoLoader.DEFAULT_ADMIN_COLOR_PRIMARY;
        }
        return adminColorPrimary;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
