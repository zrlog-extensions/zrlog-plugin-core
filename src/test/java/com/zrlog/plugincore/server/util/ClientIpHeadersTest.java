package com.zrlog.plugincore.server.util;

import com.hibegin.http.server.api.HttpRequest;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ClientIpHeadersTest {

    @Test
    public void shouldNormalizeForwardedForToRealIpHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        ClientIpHeaders.putRealIpHeader(headers, request(headers, "127.0.0.1"));

        assertEquals("203.0.113.10", headers.get(ClientIpHeaders.REAL_IP_HEADER));
    }

    @Test
    public void shouldFallbackToRemoteHostWhenHeadersMissing() {
        Map<String, String> headers = new HashMap<>();

        ClientIpHeaders.putRealIpHeader(headers, request(headers, "198.51.100.20"));

        assertEquals("198.51.100.20", headers.get(ClientIpHeaders.REAL_IP_HEADER));
    }

    @Test
    public void shouldKeepCopiedHeadersWhenAddingRealIp() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "test-agent");
        headers.put("CF-Connecting-IP", "192.0.2.30");

        Map<String, String> copiedHeaders = ClientIpHeaders.copyHeadersWithRealIp(request(headers, "127.0.0.1"));

        assertEquals("test-agent", copiedHeaders.get("User-Agent"));
        assertEquals("192.0.2.30", copiedHeaders.get(ClientIpHeaders.REAL_IP_HEADER));
    }

    private HttpRequest request(Map<String, String> headers, String remoteHost) {
        return (HttpRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getHeaderMap".equals(method.getName())) {
                        return headers;
                    }
                    if ("getRemoteHost".equals(method.getName())) {
                        return remoteHost;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                }
        );
    }
}
