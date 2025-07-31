package com.zrlog.plugincore.server.handle;

import com.google.gson.Gson;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.HexaConversionUtil;
import com.zrlog.plugin.common.IOUtil;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.data.codec.*;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.util.HttpMsgUtil;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xiaochun on 2016/2/12.
 */
public class PluginHandle implements HttpErrorHandle {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginHandle.class);

    private boolean includePath(Set<String> paths, String uri) {
        for (String path : paths) {
            String tPath = path.trim();
            if (!tPath.isEmpty()) {
                if (uri.startsWith(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PluginRequestUriInfo parseRequestUri(String uri) {
        String realUri = uri.replaceFirst("/admin/plugins/", "").replaceFirst("/p/", "").replaceFirst("/plugin/", "");
        if (StringUtils.isEmpty(realUri)) {
            return new PluginRequestUriInfo("", "");
        }
        if (realUri.startsWith("/")) {
            realUri = realUri.substring(1);
        }
        String pluginName = realUri.split("/")[0];
        String action = realUri.replaceFirst(pluginName, "");
        if (StringUtils.isEmpty(action)) {
            action = "/";
        }
        return new PluginRequestUriInfo(pluginName, action);
    }

    public static void main(String[] args) {
        PluginRequestUriInfo pluginRequestUriInfo = parseRequestUri("/admin/plugins/oss//assets/js/bootstrap-switch.js");
        System.out.println(pluginRequestUriInfo.getName() + " -> " + pluginRequestUriInfo.getAction());
    }

    private static File convertToFile(byte[] data, String saveFilePath) {
        int fileDescLength = HexaConversionUtil.byteArrayToIntH(HexaConversionUtil.subByts(data, 0, 4));
        FileDesc fileDesc = new Gson().fromJson(new String(HexaConversionUtil.subByts(data, 4, fileDescLength)), FileDesc.class);
        int dataLength = HexaConversionUtil.byteArrayToIntH(HexaConversionUtil.subByts(data, fileDescLength + 4 + 32, 4));
        byte[] fileBytes = HexaConversionUtil.subByts(data, fileDescLength + 8 + 32, dataLength);
        File resultFile = new File(saveFilePath + "/" + fileDesc.getFileName());
        IOUtil.writeBytesToFile(fileBytes, resultFile);
        return resultFile;
    }

    @Override
    public void doHandle(HttpRequest httpRequest, HttpResponse httpResponse, Throwable e) {
        boolean isLogin = Boolean.parseBoolean(httpRequest.getHeader("IsLogin"));
        httpRequest.getAttr().put("isLogin", isLogin);
        PluginRequestUriInfo pluginRequestUriInfo = parseRequestUri(httpRequest.getUri());

        if (Objects.equals(httpRequest.getHeader("DEV_MODE"), "true") || EnvKit.isDevMode()) {
            LOGGER.log(Level.INFO, "plugin name " + pluginRequestUriInfo.getName());
        }
        IOSession session = PluginConfig.getInstance().getIOSessionByPluginName(pluginRequestUriInfo.getName());
        if (Objects.isNull(session)) {
            httpResponse.renderCode(404);
            return;
        }
        if (!isLogin && !includePath(session.getPlugin().getPaths(), pluginRequestUriInfo.getAction())) {
            httpResponse.renderCode(403);
            return;
        }

        //Full Blog System ENV
        HttpRequestInfo msgBody = HttpMsgUtil.genInfo(httpRequest);
        msgBody.setUri(pluginRequestUriInfo.getAction());
        if (("/".equals(msgBody.getUri()) && !"".equals(session.getPlugin().getIndexPage()))) {
            msgBody.setUri(session.getPlugin().getIndexPage());
        }
        ActionType actionType;
        if (new File(msgBody.getUri()).getName().contains(".")) {
            actionType = ActionType.HTTP_FILE;
        } else {
            actionType = ActionType.HTTP_METHOD;
            if (httpRequest.getRequestBodyByteBuffer() != null) {
                msgBody.setRequestBody(httpRequest.getRequestBodyByteBuffer().array());
            }
            msgBody.setUri(msgBody.getUri() + ".action");
        }
        msgBody.setHeader(httpRequest.getHeaderMap());
        msgBody.setParam(httpRequest.decodeParamMap());
        int id = IdUtil.getInt();
        try {
            session.sendJsonMsg(msgBody, actionType.name(), id, MsgPacketStatus.SEND_REQUEST);
            String accessUrl = httpRequest.getHeader("AccessUrl");
            String cookie = httpRequest.getHeader("Cookie");
            if (accessUrl == null) {
                accessUrl = "";
            }
            if (cookie == null) {
                cookie = "";
            }
            session.getAttr().put("accessUrl", accessUrl);
            session.getAttr().put("cookie", cookie);
            MsgPacket responseMsgPacket = session.getResponseMsgPacketByMsgId(id);
            if (Objects.isNull(responseMsgPacket)) {
                LOGGER.warning(httpRequest.getUri() + " -> error, plugin " + session.getPlugin().getShortName() + " not response");
                httpResponse.renderCode(500);
                return;
            }
            if (responseMsgPacket.getMethodStr().equals(ActionType.HTTP_ATTACHMENT_FILE.name())) {
                String tempDirPath = System.getProperty("java.io.tmpdir");
                File tempDir = new File(tempDirPath);

                if (!tempDir.exists()) {
                    tempDir.mkdirs(); // 确保目录存在
                }
                File file = convertToFile(responseMsgPacket.getData().array(), tempDirPath);
                try {
                    httpResponse.renderFile(file);
                    return;
                } finally {
                    file.delete();
                }
            }
            String ext;
            if (responseMsgPacket.getContentType() == ContentType.JSON) {
                ext = "json";
            } else if (responseMsgPacket.getContentType() == ContentType.HTML) {
                ext = "html";
            } else if (responseMsgPacket.getContentType() == ContentType.XML) {
                ext = "xml";
            } else {
                ext = httpRequest.getUri().substring(httpRequest.getUri().lastIndexOf(".") + 1);
            }
            InputStream in = new ByteArrayInputStream(responseMsgPacket.getData().array());
            httpResponse.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(ext));
            httpResponse.write(in, responseMsgPacket.getStatus() == MsgPacketStatus.RESPONSE_SUCCESS ? 200 : 500);
        } finally {
            session.getPipeMap().remove(id);
        }
    }
}
