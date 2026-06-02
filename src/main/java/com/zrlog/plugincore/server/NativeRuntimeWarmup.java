package com.zrlog.plugincore.server;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.MsgPacketDispose;
import com.zrlog.plugin.api.Capability;
import com.zrlog.plugin.api.IPluginService;
import com.zrlog.plugin.api.ScheduledCapability;
import com.zrlog.plugin.api.Service;
import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.KvRepository;
import com.zrlog.plugin.client.ClientActionHandler;
import com.zrlog.plugin.client.NioClient;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.CapabilityInvokeRequest;
import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.message.NotificationRequest;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugin.message.SchedulerQueryRequest;
import com.zrlog.plugin.message.SchedulerQueryResult;
import com.zrlog.plugin.message.SchedulerUpdateRequest;
import com.zrlog.plugin.message.SchedulerUpdateResult;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugincore.server.runtime.capability.CapabilityRegistrationService;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.notification.NotificationDeliveryStore;
import com.zrlog.plugincore.server.runtime.notification.NotificationProviderResolver;
import com.zrlog.plugincore.server.runtime.notification.NotificationPublishResult;
import com.zrlog.plugincore.server.runtime.notification.NotificationRuntime;
import com.zrlog.plugincore.server.runtime.notification.NotificationSetting;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationStore;
import com.zrlog.plugincore.server.runtime.scheduler.PluginAutomation;
import com.zrlog.plugincore.server.runtime.scheduler.RuntimeAutomationService;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerQueryService;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerSetting;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerUpdateService;
import com.zrlog.plugincore.server.runtime.service.ServiceSetting;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class NativeRuntimeWarmup {

    private NativeRuntimeWarmup() {
    }

    static Result run() {
        Gson gson = new Gson();
        warmupActionTypes();
        int annotatedCapabilityCount = warmupAnnotatedCapabilityRead(gson);
        int actionDispatchCount = warmupActionDispatch(gson);

        Plugin plugin = gson.fromJson(samplePluginJson(), Plugin.class);
        String initPayload = gson.toJson(plugin);
        KvRepository kvStore = new MemoryKvRepository();
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        List<PluginCapability> capabilities = new CapabilityRegistrationService(capabilityStore)
                .registerCapabilitiesFromInitPayload(plugin, initPayload);

        AutomationStore automationStore = new AutomationStore(kvStore);
        ZonedDateTime now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        List<PluginAutomation> automations = new RuntimeAutomationService(automationStore, capabilityStore, new BasicCronParser())
                .ensureDefaultAutomations(capabilities, now);

        SchedulerQueryRequest schedulerQueryRequest = gson.fromJson("{"
                + "\"capabilityKey\":\"native.scan\""
                + "}", SchedulerQueryRequest.class);
        SchedulerQueryResult schedulerQueryResult = new SchedulerQueryService(automationStore, capabilityStore)
                .query(plugin, schedulerQueryRequest);

        SchedulerUpdateRequest schedulerRequest = gson.fromJson("{"
                + "\"capabilityKey\":\"native.scan\","
                + "\"cron\":\"*/10 * * * *\","
                + "\"enabled\":true"
                + "}", SchedulerUpdateRequest.class);
        SchedulerUpdateResult schedulerResult = new SchedulerUpdateService(automationStore, capabilityStore, new BasicCronParser())
                .update(plugin, schedulerRequest, now);

        gson.toJson(gson.fromJson("{"
                + "\"enabled\":true,"
                + "\"externalHost\":\"https://example.com\","
                + "\"providers\":[{\"id\":\"default\",\"enabled\":true,\"secret\":\"secret\"}]"
                + "}", SchedulerSetting.class));
        gson.toJson(gson.fromJson("{"
                + "\"defaultProviders\":{\"comment\":{\"serviceName\":\"comment\",\"pluginId\":\"native-plugin\","
                + "\"capabilityKey\":\"native.comment\"}}"
                + "}", ServiceSetting.class));

        NotificationSetting notificationSetting = gson.fromJson("{"
                + "\"defaultProviders\":{\"email\":{\"pluginId\":\"native-plugin\","
                + "\"capabilityKey\":\"native.email\",\"channel\":\"email\"}}"
                + "}", NotificationSetting.class);
        gson.toJson(notificationSetting);
        NotificationRequest notificationRequest = gson.fromJson("{"
                + "\"sourcePluginId\":\"native-plugin\","
                + "\"sourcePluginName\":\"Native Plugin\","
                + "\"sourceCapabilityKey\":\"native.scan\","
                + "\"eventType\":\"warmup\","
                + "\"notificationType\":\"runtime\","
                + "\"channels\":[\"email\"],"
                + "\"title\":\"warmup\","
                + "\"content\":\"warmup\","
                + "\"level\":\"info\","
                + "\"requestId\":\"warmup-request\","
                + "\"traceId\":\"warmup-trace\","
                + "\"payload\":{\"ok\":true}"
                + "}", NotificationRequest.class);
        NotificationPublishResult publishResult = new NotificationRuntime(
                capabilityStore,
                new NotificationDeliveryStore(kvStore),
                new NotificationProviderResolver(),
                notificationSetting,
                (pluginId, capabilityKey, payload, context) -> {
                    CapabilityInvokeRequest invokeRequest = new CapabilityInvokeRequest();
                    invokeRequest.setPluginId(pluginId);
                    invokeRequest.setCapabilityKey(capabilityKey);
                    invokeRequest.setSource(context == null ? null : context.getSource());
                    invokeRequest.setPayload(payload);
                    gson.toJson(gson.fromJson(gson.toJson(invokeRequest), CapabilityInvokeRequest.class));
                    CapabilityInvokeResult result = new CapabilityInvokeResult();
                    result.setSuccess(true);
                    result.setData(new HashMap<String, Object>());
                    return gson.fromJson(gson.toJson(result), CapabilityInvokeResult.class);
                })
                .publish(notificationRequest);

        return new Result(capabilities.size(), automations.size(), schedulerQueryResult.isSuccess(), !schedulerResult.isSuccess(),
                publishResult.getSuccessCount(), publishResult.getFailedCount(), annotatedCapabilityCount, actionDispatchCount);
    }

    private static void warmupActionTypes() {
        ActionType.valueOf(ActionType.CAPABILITY_INVOKE.name());
        ActionType.valueOf(ActionType.NOTIFICATION_PUBLISH.name());
        ActionType.valueOf(ActionType.NOTIFICATION_CHANNEL_QUERY.name());
        ActionType.valueOf(ActionType.SCHEDULER_QUERY.name());
        ActionType.valueOf(ActionType.SCHEDULER_UPDATE.name());
    }

    @SuppressWarnings("unchecked")
    private static int warmupAnnotatedCapabilityRead(Gson gson) {
        try {
            Method method = NioClient.class.getDeclaredMethod("readCapabilities", Class.class);
            method.setAccessible(true);
            NioClient client = new NioClient();
            List<PluginCapability> scheduled = (List<PluginCapability>) method.invoke(client, WarmupScheduledService.class);
            List<PluginCapability> notification = (List<PluginCapability>) method.invoke(client, WarmupNotificationService.class);
            gson.toJson(scheduled);
            gson.toJson(notification);
            return scheduled.size() + notification.size();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to warm up plugin capability annotations", e);
        }
    }

    private static int warmupActionDispatch(Gson gson) {
        CountingActionHandler actionHandler = new CountingActionHandler(gson);
        MsgPacketDispose dispose = new MsgPacketDispose();
        dispose.handler(null, packet(new CapabilityInvokeRequest(), ActionType.CAPABILITY_INVOKE), actionHandler);
        dispose.handler(null, packet(new NotificationRequest(), ActionType.NOTIFICATION_PUBLISH), actionHandler);
        dispose.handler(null, packet(new HashMap<String, Object>(), ActionType.NOTIFICATION_CHANNEL_QUERY), actionHandler);
        dispose.handler(null, packet(new SchedulerQueryRequest(), ActionType.SCHEDULER_QUERY), actionHandler);
        dispose.handler(null, packet(new SchedulerUpdateRequest(), ActionType.SCHEDULER_UPDATE), actionHandler);
        return actionHandler.getCount();
    }

    private static MsgPacket packet(Object data, ActionType actionType) {
        return new MsgPacket(data, ContentType.JSON, MsgPacketStatus.SEND_REQUEST, actionType.ordinal() + 1, actionType.name());
    }

    private static String samplePluginJson() {
        return "{"
                + "\"id\":\"native-plugin\","
                + "\"shortName\":\"native\","
                + "\"name\":\"Native Plugin\","
                + "\"services\":[\"native.scan\",\"native.email\"],"
                + "\"capabilities\":[{"
                + "\"key\":\"native.scan\","
                + "\"serviceName\":\"native.scan\","
                + "\"type\":\"scheduled\","
                + "\"label\":\"Native scan\","
                + "\"description\":\"Native scan warmup\","
                + "\"exposure\":[\"scheduler\"],"
                + "\"riskLevel\":\"low\","
                + "\"readOnly\":false,"
                + "\"requiresConfirmation\":false,"
                + "\"timeoutSeconds\":600,"
                + "\"concurrency\":1,"
                + "\"enabled\":true,"
                + "\"defaultCron\":\"*/5 * * * *\","
                + "\"timezone\":\"system\""
                + "},{"
                + "\"key\":\"native.email\","
                + "\"serviceName\":\"native.email\","
                + "\"type\":\"notification_channel\","
                + "\"label\":\"Native email\","
                + "\"description\":\"Native email warmup\","
                + "\"exposure\":[\"notification\"],"
                + "\"riskLevel\":\"medium\","
                + "\"readOnly\":false,"
                + "\"requiresConfirmation\":false,"
                + "\"timeoutSeconds\":600,"
                + "\"concurrency\":1,"
                + "\"enabled\":true,"
                + "\"channel\":\"email\""
                + "}]"
                + "}";
    }

    static final class Result {
        private final int capabilityCount;
        private final int automationCount;
        private final boolean schedulerQuerySuccess;
        private final boolean schedulerUpdateRejected;
        private final int notificationSuccessCount;
        private final int notificationFailedCount;
        private final int annotatedCapabilityCount;
        private final int actionDispatchCount;

        Result(int capabilityCount, int automationCount, boolean schedulerQuerySuccess, boolean schedulerUpdateRejected,
               int notificationSuccessCount, int notificationFailedCount, int annotatedCapabilityCount, int actionDispatchCount) {
            this.capabilityCount = capabilityCount;
            this.automationCount = automationCount;
            this.schedulerQuerySuccess = schedulerQuerySuccess;
            this.schedulerUpdateRejected = schedulerUpdateRejected;
            this.notificationSuccessCount = notificationSuccessCount;
            this.notificationFailedCount = notificationFailedCount;
            this.annotatedCapabilityCount = annotatedCapabilityCount;
            this.actionDispatchCount = actionDispatchCount;
        }

        int getCapabilityCount() {
            return capabilityCount;
        }

        int getAutomationCount() {
            return automationCount;
        }

        boolean isSchedulerQuerySuccess() {
            return schedulerQuerySuccess;
        }

        boolean isSchedulerUpdateRejected() {
            return schedulerUpdateRejected;
        }

        int getNotificationSuccessCount() {
            return notificationSuccessCount;
        }

        int getNotificationFailedCount() {
            return notificationFailedCount;
        }

        int getAnnotatedCapabilityCount() {
            return annotatedCapabilityCount;
        }

        int getActionDispatchCount() {
            return actionDispatchCount;
        }
    }

    private static final class CountingActionHandler extends ClientActionHandler {
        private final Gson gson;
        private int count;

        private CountingActionHandler(Gson gson) {
            this.gson = gson;
        }

        @Override
        public void capabilityInvoke(IOSession session, MsgPacket msgPacket) {
            gson.toJson(msgPacket.convertToClass(CapabilityInvokeRequest.class));
            gson.toJson(new CapabilityInvokeResult());
            count++;
        }

        @Override
        public void notificationPublish(IOSession session, MsgPacket msgPacket) {
            gson.toJson(msgPacket.convertToClass(NotificationRequest.class));
            count++;
        }

        @Override
        public void notificationChannelQuery(IOSession session, MsgPacket msgPacket) {
            gson.toJson(new HashMap<String, Object>());
            count++;
        }

        @Override
        public void schedulerQuery(IOSession session, MsgPacket msgPacket) {
            gson.toJson(msgPacket.convertToClass(SchedulerQueryRequest.class));
            gson.toJson(new SchedulerQueryResult());
            count++;
        }

        @Override
        public void schedulerUpdate(IOSession session, MsgPacket msgPacket) {
            gson.toJson(msgPacket.convertToClass(SchedulerUpdateRequest.class));
            SchedulerUpdateResult result = new SchedulerUpdateResult();
            result.setSuccess(false);
            result.setErrorMessage("Scheduler writes are managed by plugin-core");
            gson.toJson(result);
            count++;
        }

        private int getCount() {
            return count;
        }
    }

    @Service("native.scan")
    @Capability(key = "native.scan", riskLevel = "medium", readOnly = true)
    @ScheduledCapability(key = "native.scan", label = "Native scan", defaultCron = "*/5 * * * *")
    public static final class WarmupScheduledService implements IPluginService {
        @Override
        public void handle(IOSession session, MsgPacket msgPacket) {
        }
    }

    @Service("native.email")
    @Capability(key = "native.email",
            type = "notification_channel",
            label = "Native email",
            exposure = {"notification"},
            channel = "email")
    public static final class WarmupNotificationService implements IPluginService {
        @Override
        public void handle(IOSession session, MsgPacket msgPacket) {
        }
    }

    private static final class MemoryKvRepository implements KvRepository {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
