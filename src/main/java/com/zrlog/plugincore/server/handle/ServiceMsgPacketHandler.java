package com.zrlog.plugincore.server.handle;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.plugin.PluginSessions;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderResolver;
import com.zrlog.plugincore.server.runtime.service.ServiceSetting;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ServiceMsgPacketHandler {

    private final IOSession session;

    public ServiceMsgPacketHandler(IOSession session) {
        this.session = session;
    }

    private static PluginVO findPluginByService(String service, String pluginId) {
        for (PluginVO pluginVO : PluginCoreDAO.getInstance().loadSnapshot().getPluginInfoMap().values()) {
            if (pluginVO.getPlugin() != null
                    && (pluginId == null || Objects.equals(pluginId, pluginVO.getPlugin().getId()))
                    && pluginVO.getPlugin().getServices() != null
                    && pluginVO.getPlugin().getServices().contains(service)) {
                return pluginVO;
            }
        }
        return null;
    }

    public static IOSession getServiceSessionWithRetry(String serviceName, int retryCount) throws InterruptedException {
        return getServiceSessionWithRetry(serviceName, null, retryCount);
    }

    private static IOSession getServiceSessionWithRetry(String serviceName, String pluginId, int retryCount) throws InterruptedException {
        int loopCount = Math.max(retryCount, 1);
        PluginVO pluginVO = findPluginByService(serviceName, pluginId);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return null;
        }
        String servicePluginId = pluginVO.getPlugin().getId();
        IOSession serviceSession = PluginSessions.getLocalSessionByPluginId(servicePluginId);
        if (Objects.nonNull(serviceSession)) {
            return serviceSession;
        }
        if (!ensureServiceStarted(pluginVO)) {
            return null;
        }
        for (int i = 0; i < loopCount; i++) {
            serviceSession = PluginSessions.getLocalSessionByPluginId(servicePluginId);
            if (Objects.nonNull(serviceSession)) {
                return serviceSession;
            }
            if (i + 1 < loopCount) {
                Thread.sleep(1000);
            }
        }
        return null;
    }

    private static boolean ensureServiceStarted(PluginVO pluginVO) {
        return runtimeStateService().ensureStarted(pluginVO.getPlugin().getId());
    }

    public void doHandle(final MsgPacket msgPacket) {
        Map<String, Object> map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
        if (map == null || map.get("name") == null) {
            session.sendJsonMsg(errorResponse("Service name is required"), msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
            return;
        }
        String name = map.get("name").toString();
        PluginRuntimeStateService stateService = null;
        String targetPluginId = null;
        String targetPluginName = null;
        try {
            PluginCapability provider = resolveServiceProvider(name);
            IOSession serviceSession = provider == null
                    ? getServiceSessionWithRetry(name, 60)
                    : getServiceSessionWithRetry(name, provider.getPluginId(), 60);
            if (Objects.isNull(serviceSession) || serviceSession.getPlugin() == null) {
                throw new RuntimeException("Not found serviceSession " + name);
            }
            targetPluginId = serviceSession.getPlugin().getId();
            targetPluginName = PluginSessions.nameOrShortName(serviceSession.getPlugin());
            PluginRuntimeStateService invocationStateService = PluginRuntimeStates.newStateService(serviceSession);
            stateService = invocationStateService;
            invocationStateService.markInvocationStart(targetPluginId, targetPluginName);
            final String invocationPluginId = targetPluginId;
            final String invocationPluginName = targetPluginName;
            final PluginRuntimeStateService callbackStateService = invocationStateService;
            // 消息中转
            serviceSession.requestService(name, map, responseMsgPacket -> {
                try {
                    responseMsgPacket.setMsgId(msgPacket.getMsgId());
                    session.sendMsg(responseMsgPacket);
                } finally {
                    callbackStateService.markInvocationEnd(invocationPluginId, invocationPluginName,
                            responseMsgPacket.getStatus() == MsgPacketStatus.RESPONSE_SUCCESS ? null : "service response error");
                }
            });
        } catch (Exception e) {
            if (targetPluginId != null && stateService != null) {
                stateService.markInvocationEnd(targetPluginId, targetPluginName, e.getMessage());
            }
            // not found service response error
            session.sendJsonMsg(errorResponse(e.getMessage()), msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
        }
    }

    private PluginCapability resolveServiceProvider(String serviceName) {
        ServiceSetting setting = PluginCoreDAO.getInstance().loadSnapshot().getSetting().getService();
        return new ServiceProviderResolver()
                .resolve(serviceName, capabilityStore().listByType("service"), setting)
                .orElse(null);
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 1);
        response.put("message", message);
        return response;
    }

    private static PluginRuntimeStateService runtimeStateService() {
        return new PluginRuntimeStateService(new PluginRuntimeStateStore(new WebsiteRuntimeKvStore()), new DefaultPluginRuntimeStarter());
    }

    private CapabilityStore capabilityStore() {
        return new CapabilityStore(kvStore());
    }

    private KvRepository kvStore() {
        return new WebsiteRuntimeKvStore();
    }
}
