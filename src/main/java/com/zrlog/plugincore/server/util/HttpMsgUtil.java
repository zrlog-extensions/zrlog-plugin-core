package com.zrlog.plugincore.server.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.plugin.data.codec.HttpRequestInfo;

import java.util.Objects;

public class HttpMsgUtil {

    private HttpMsgUtil() {
    }

    public static HttpRequestInfo genInfo(HttpRequest request) {
        HttpRequestInfo msgBody = new HttpRequestInfo();
        msgBody.setFullUrl(request.getHeader("Full-Url"));
        msgBody.setUserName(Objects.requireNonNullElse(request.getHeader("LoginUserName"), ""));
        if (request.getHeader("LoginUserId") != null && !"".equals(request.getHeader("LoginUserId"))) {
            msgBody.setUserId(Integer.valueOf(request.getHeader("LoginUserId")));
        } else {
            msgBody.setUserId(-1);
        }
        msgBody.setVersion(request.getHeader("Blog-Version"));
        msgBody.setAccessUrl(request.getHeader("AccessUrl"));
        return msgBody;
    }
}
