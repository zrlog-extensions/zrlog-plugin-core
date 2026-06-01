package com.zrlog.plugincore.server.web.handler;

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
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessions;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.util.AdminTheme;
import com.zrlog.plugincore.server.util.HttpMsgUtil;
import com.zrlog.plugincore.server.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
        String pluginShortName = realUri.split("/")[0];
        String action = realUri.replaceFirst(pluginShortName, "");
        if (StringUtils.isEmpty(action)) {
            action = "/";
        }
        return new PluginRequestUriInfo(pluginShortName, action);
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
        if (isInternalUri(pluginRequestUriInfo.getName())) {
            httpResponse.renderCode(404);
            return;
        }

        boolean devMode = EnvKit.isDevMode();
        boolean devRequest = devMode || isDevRequest(httpRequest);
        if (devRequest) {
            LOGGER.log(Level.INFO, "plugin name " + pluginRequestUriInfo.getName());
        }
        IOSession session = getReadySession(pluginRequestUriInfo.getName());
        if (Objects.isNull(session)) {
            httpResponse.renderCode(503);
            return;
        }
        if (!devMode && !isLogin && !includePath(session.getPlugin().getPaths(), pluginRequestUriInfo.getAction())) {
            httpResponse.renderCode(403);
            return;
        }

        PluginRuntimeStateService stateService = PluginRuntimeStates.newStateService(session);
        String pluginId = session.getPlugin().getId();
        String pluginName = PluginSessions.nameOrShortName(session.getPlugin());
        String errorMessage = null;
        stateService.markInvocationStart(pluginId, pluginName);
        //Full Blog System ENV
        int id = IdUtil.getInt();
        try {
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
                    msgBody.setRequestBody(requestBodyBytes(httpRequest.getRequestBodyByteBuffer()));
                }
                msgBody.setUri(msgBody.getUri() + ".action");
            }
            AdminTheme.applyTo(msgBody, httpRequest);
            msgBody.setParam(httpRequest.decodeParamMap());
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
                errorMessage = "plugin " + session.getPlugin().getShortName() + " not response";
                LOGGER.warning(httpRequest.getUri() + " -> error, " + errorMessage);
                httpResponse.renderCode(500);
                return;
            }
            if (responseMsgPacket.getStatus() == MsgPacketStatus.RESPONSE_ERROR) {
                errorMessage = "plugin " + session.getPlugin().getShortName() + " response error";
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
            String ext = getExt(httpRequest, responseMsgPacket);
            InputStream in = new ByteArrayInputStream(responseMsgPacket.getData().array());
            httpResponse.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(ext));
            httpResponse.write(in, responseMsgPacket.getStatus() == MsgPacketStatus.RESPONSE_SUCCESS ? 200 : 500);
        } catch (RuntimeException ex) {
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            session.getPipeMap().remove(id);
            stateService.markInvocationEnd(pluginId, pluginName, errorMessage);
        }
    }

    private byte[] requestBodyBytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private IOSession getReadySession(String pluginShortName) {
        return PluginSessions.getOrStartLocalSessionByPluginShortName(pluginShortName);
    }

    private boolean isDevRequest(HttpRequest httpRequest) {
        return Objects.equals(httpRequest.getHeader("DEV_MODE"), "true");
    }

    private boolean isInternalUri(String firstSegment) {
        return Objects.equals("api", firstSegment)
                || Objects.equals("static", firstSegment)
                || Objects.equals("runtime-scheduler", firstSegment)
                || Objects.equals("runtime-states", firstSegment)
                || Objects.equals("runtime-notification", firstSegment);
    }

    private static String getExt(HttpRequest httpRequest, MsgPacket responseMsgPacket) {
        if (responseMsgPacket.getContentType() == ContentType.JSON) {
            return "json";
        } else if (responseMsgPacket.getContentType() == ContentType.HTML) {
            return "html";
        } else if (responseMsgPacket.getContentType() == ContentType.XML) {
            return "xml";
        } else if (responseMsgPacket.getContentType() == ContentType.IMAGE_SVG_XML) {
            return "svg";
        }
        return httpRequest.getUri().substring(httpRequest.getUri().lastIndexOf(".") + 1);
    }
}
