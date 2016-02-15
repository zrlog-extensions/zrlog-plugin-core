package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.IOUtil;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpUtils {


    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static byte[] sendGetRequest(String url, Map<String, String> headers) throws Exception {
        return IOUtil.getByteByInputStream(doGetRequest(url, headers));
    }

    public static InputStream doGetRequest(String url, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        headers.forEach(builder::header);
        builder.uri(new URI(url));
        HttpRequest httpRequest = builder.GET().build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream()).body();
    }
}
