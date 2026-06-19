package com.zrlog.plugincore.server.web.handler;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.*;
import com.zrlog.plugin.common.type.PluginVersion;
import com.zrlog.plugin.data.codec.*;
import com.zrlog.plugincore.server.runtime.pwa.PluginPwaResources;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugincore.server.runtime.plugin.log.PluginLogContext;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessions;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.util.HttpMsgUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class PluginHttpStream {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginHttpStream.class);

    private static final PluginPwaResources PWA_RESOURCES = new PluginPwaResources();


    private final IOSession session;
    private final PluginRequestUriInfo pluginRequestUriInfo;
    private final HttpRequest httpRequest;
    private final HttpResponse httpResponse;

    public PluginHttpStream(IOSession session, PluginRequestUriInfo pluginRequestUriInfo, HttpRequest httpRequest, HttpResponse httpResponse) {
        this.session = session;
        this.pluginRequestUriInfo = pluginRequestUriInfo;
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
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

    public void handle() {
        PluginRuntimeStateService stateService = PluginRuntimeStates.newStateService(session);
        String pluginId = session.getPlugin().getId();
        String pluginName = PluginSessions.nameOrShortName(session.getPlugin());
        String errorMessage = null;
        stateService.markInvocationStart(pluginId, pluginName);
        //Full Blog System ENV
        int id = IdUtil.getInt();
        try {
            if (PWA_RESOURCES.renderIfMatched(session.getPlugin(), pluginRequestUriInfo, httpResponse)) {
                return;
            }
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

                msgBody.setUri(msgBody.getUri() + ".action");
            }
            if (PluginVersionUtils.getPluginVersion(session.getPlugin()) == PluginVersion.V1) {
                Map convert = ResultBeanUtils.convert(msgBody, Map.class);
                //
                convert.put("class", "com.fzb.zrlog.plugin.data.codec.HttpRequestInfo");
                convert.put("userId", 0);
                session.sendJsonMsg(convert, actionType.name(), id, MsgPacketStatus.SEND_REQUEST);
            } else {
                session.sendJsonMsg(msgBody, actionType.name(), id, MsgPacketStatus.SEND_REQUEST);
            }
            MsgPacket responseMsgPacket = session.getResponseMsgPacketByMsgId(id);
            if (Objects.isNull(responseMsgPacket)) {
                errorMessage = "plugin " + session.getPlugin().getShortName() + " not response";
                LOGGER.warning(PluginLogContext.prefix(httpRequest.getUri() + " -> error, " + errorMessage));
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
