package com.zrlog.plugincore.server.runtime.plugin.process;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginProcessInfo;
import com.zrlog.plugincore.server.runtime.plugin.session.PluginSessionRegistry;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeInstanceView;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.channels.Channel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginProcessQueryServiceTest {

    @Test
    public void shouldEnrichProcessInfoFromSessionAndRuntimeState() throws Exception {
        IOSession session = testSession("plugin-a", "reminder", "Reminder", "instance-a", 42L);
        PluginProcessInfo rawInfo = new PluginProcessInfo();
        rawInfo.setAlive(Boolean.TRUE);
        rawInfo.setResidentMemoryBytes(4096L);
        rawInfo.setThreadCount(12);
        PluginRuntimeInstanceView runtimeView = runtimeView();
        PluginProcessQueryService service = new PluginProcessQueryService(
                () -> Collections.singletonList(session),
                () -> Collections.singletonList(runtimeView),
                (currentSession, timeout) -> rawInfo);

        List<PluginRuntimeInstanceView> items = service.query();

        assertEquals(1, items.size());
        PluginRuntimeInstanceView info = items.get(0);
        assertEquals("plugin-a", info.getPluginId());
        assertEquals("Reminder", info.getPluginName());
        assertEquals("instance-a", info.getInstanceId());
        assertEquals(Long.valueOf(42L), info.getProcessId());
        assertEquals("process", info.getRuntimeMode());
        assertEquals("ready", info.getStatus());
        assertEquals("executing", info.getEffectiveStatus());
        assertEquals(Integer.valueOf(3), info.getActiveInvocationCount());
        assertEquals(Long.valueOf(4096L), info.getResidentMemoryBytes());
        assertEquals(Integer.valueOf(12), info.getThreadCount());
        assertTrue(info.getProcessAlive());
    }

    @Test
    public void shouldReturnFallbackInfoWhenSessionChannelIsClosed() throws Exception {
        TestSession testSession = testSessionWithChannel("plugin-a", "reminder", "Reminder", "instance-a", 42L);
        testSession.channel.close();
        AtomicBoolean queried = new AtomicBoolean(false);
        PluginRuntimeInstanceView runtimeView = runtimeView();
        PluginProcessQueryService service = new PluginProcessQueryService(
                () -> Collections.singletonList(testSession.session),
                () -> Collections.singletonList(runtimeView),
                (currentSession, timeout) -> {
                    queried.set(true);
                    return new PluginProcessInfo();
                });

        List<PluginRuntimeInstanceView> items = service.query();

        assertEquals(1, items.size());
        PluginRuntimeInstanceView info = items.get(0);
        assertEquals("plugin-a", info.getPluginId());
        assertEquals("instance-a", info.getInstanceId());
        assertFalse(info.getProcessAlive());
        assertEquals("Plugin process channel is closed", info.getProcessErrorMessage());
        assertFalse(queried.get());
    }

    private PluginRuntimeInstanceView runtimeView() {
        PluginRuntimeInstanceView view = new PluginRuntimeInstanceView();
        view.setPluginId("plugin-a");
        view.setPluginName("Reminder");
        view.setInstanceId("instance-a");
        view.setRuntimeMode("process");
        view.setProcessId(42L);
        view.setLocal(Boolean.TRUE);
        view.setStatus("ready");
        view.setEffectiveStatus("executing");
        view.setStartedAt(1000L);
        view.setReadyAt(2000L);
        view.setLastActiveAt(3000L);
        view.setHeartbeatAt(4000L);
        view.setLeaseExpiresAt(5000L);
        view.setActiveInvocationCount(3);
        return view;
    }

    private IOSession testSession(String pluginId, String shortName, String name,
                                  String instanceId, Long processId) throws Exception {
        return testSessionWithChannel(pluginId, shortName, name, instanceId, processId).session;
    }

    private TestSession testSessionWithChannel(String pluginId, String shortName, String name,
                                               String instanceId, Long processId) throws Exception {
        IOSession session = (IOSession) unsafe().allocateInstance(IOSession.class);
        setField(session, "systemAttr", new ConcurrentHashMap<String, Object>());
        Plugin plugin = new Plugin();
        plugin.setId(pluginId);
        plugin.setShortName(shortName);
        plugin.setName(name);
        setField(session, "plugin", plugin);
        FakeChannel channel = new FakeChannel();
        session.getSystemAttr().put("_channel", channel);
        session.getSystemAttr().put(PluginSessionRegistry.SESSION_ID_ATTR, instanceId);
        session.getSystemAttr().put(PluginSessionRegistry.PROCESS_ID_ATTR, processId);
        return new TestSession(session, channel);
    }

    private Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = IOSession.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class TestSession {
        private final IOSession session;
        private final FakeChannel channel;

        private TestSession(IOSession session, FakeChannel channel) {
            this.session = session;
            this.channel = channel;
        }
    }

    private static class FakeChannel implements Channel {
        private boolean open = true;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
