package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.IOUtil;
import com.zrlog.plugin.data.codec.BaseHttpRequestInfo;
import com.zrlog.plugin.data.codec.HttpResponseInfo;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public static HttpResponseInfo doRequest(BaseHttpRequestInfo httpRequestInfo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (Objects.nonNull(httpRequestInfo.getHeader())) {
            httpRequestInfo.getHeader().forEach(builder::header);
        }
        builder.uri(new URI(httpRequestInfo.getAccessUrl()));
        if (httpRequestInfo.getRequestBody() == null) {
            httpRequestInfo.setRequestBody(new byte[0]);
        }
        HttpRequest httpRequest = builder.method(httpRequestInfo.getHttpMethod().name(), HttpRequest.BodyPublishers.ofByteArray(httpRequestInfo.getRequestBody())).build();
        HttpResponse<byte[]> send = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        HttpResponseInfo httpResponseInfo = new HttpResponseInfo();
        httpResponseInfo.setHeader(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> header : send.headers().map().entrySet()) {
            httpResponseInfo.getHeader().put(header.getKey(), header.getValue().get(0));
        }
        httpResponseInfo.setResponseBody(send.body());
        httpResponseInfo.setStatusCode(send.statusCode());
        return httpResponseInfo;

    }
}
