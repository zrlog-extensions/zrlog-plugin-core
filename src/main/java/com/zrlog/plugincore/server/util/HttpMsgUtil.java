package com.zrlog.plugincore.server.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.type.RunType;

public class HttpMsgUtil {

    private HttpMsgUtil() {
    }

    public static HttpRequestInfo genInfo(HttpRequest request) {
        HttpRequestInfo msgBody = new HttpRequestInfo();
        msgBody.setFullUrl(request.getHeader("Full-Url"));
        msgBody.setUserName(request.getHeader("LoginUserName"));
        if (request.getHeader("LoginUserId") != null && !"".equals(request.getHeader("LoginUserId"))) {
            msgBody.setUserId(Integer.valueOf(request.getHeader("LoginUserId")));
        }
        msgBody.setVersion(request.getHeader("Blog-Version"));
        msgBody.setAccessUrl(request.getHeader("AccessUrl"));
        return msgBody;
    }
}
