package com.zrlog.plugincore.server.handle;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ServiceMsgPacketHandler {

    private final IOSession session;

    public ServiceMsgPacketHandler(IOSession session) {
        this.session = session;
    }

    private static IOSession getIOSessionByService(String service) {
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().getPluginInfoMap().values()) {
            if (pluginVO.getPlugin().getServices().contains(service)) {
                return PluginConfig.getInstance().getSessionMap().get(pluginVO.getPlugin().getId());
            }
        }
        return null;
    }

    public static IOSession getServiceSessionWithRetry(String serviceName, int retryCount) throws InterruptedException {
        int loopCount = Math.max(retryCount, 1);
        for (int i = 0; i < loopCount; i++) {
            IOSession serviceSession = getIOSessionByService(serviceName);
            if (Objects.isNull(serviceSession)) {
                Thread.sleep(1000);
                continue;
            }
            return serviceSession;
        }
        return null;
    }

    public void doHandle(final MsgPacket msgPacket) {
        Map<String, Object> map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
        String name = map.get("name").toString();
        try {
            IOSession serviceSession = getServiceSessionWithRetry(name, 60);
            if (Objects.isNull(serviceSession)) {
                throw new RuntimeException("Not found serviceSession " + name);
            }
            // 消息中转
            serviceSession.requestService(name, map, responseMsgPacket -> {
                responseMsgPacket.setMsgId(msgPacket.getMsgId());
                session.sendMsg(responseMsgPacket);
            });
        } catch (Exception e) {
            // not found service response error
            Map<String, Object> response = new HashMap<>();
            response.put("code", 1);
            response.put("message", e.getMessage());
            session.sendJsonMsg(response, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
        }
    }
}
