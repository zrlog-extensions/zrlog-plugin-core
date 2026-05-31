package com.zrlog.plugincore.server.runtime.state;

import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PluginRuntimeStateServiceTest {

    @Test
    public void shouldStartPluginAndMarkReady() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        FakeStarter starter = new FakeStarter();
        PluginRuntimeStateService service = new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), starter);

        assertTrue(service.ensureStarted("plugin-a"));

        PluginRuntimeState state = new PluginRuntimeStateStore(kvStore).find("plugin-a").get();
        assertEquals("ready", state.getStatus());
        assertEquals("reminder", state.getPluginName());
        assertEquals(1, starter.startCount);
        assertEquals(3, kvStore.getCount(PluginRuntimeStateStore.KEY));
    }

    @Test
    public void shouldNotWriteRuntimeStateWhenPluginAlreadyStarted() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateService service = new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), new StartedStarter());

        assertTrue(service.ensureStarted("plugin-a"));

        assertEquals(0, kvStore.getCount(PluginRuntimeStateStore.KEY));
        assertEquals(0, kvStore.putCount(PluginRuntimeStateStore.KEY));
    }

    @Test
    public void shouldTrackInvocationCountAndLastError() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateService service = new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), new FakeStarter());

        service.markInvocationStart("plugin-a", "reminder");
        PluginRuntimeState executingState = new PluginRuntimeStateStore(kvStore).find("plugin-a").get();
        assertEquals("ready", executingState.getStatus());
        assertEquals(Integer.valueOf(1), executingState.getActiveInvocationCount());

        service.markInvocationEnd("plugin-a", "reminder", "boom");

        PluginRuntimeState state = new PluginRuntimeStateStore(kvStore).find("plugin-a").get();
        assertEquals("ready", state.getStatus());
        assertEquals(Integer.valueOf(0), state.getActiveInvocationCount());
        assertEquals("boom", state.getLastError());
    }

    @Test
    public void shouldMarkInitializingAndStoppingBeforeReadyTransitions() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateService service = new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), new FakeStarter());

        service.markStarting("plugin-a", "reminder", "native");
        service.markInitializing("plugin-a", "reminder", null);
        assertEquals("initializing", new PluginRuntimeStateStore(kvStore).find("plugin-a").get().getStatus());
        assertEquals("native", new PluginRuntimeStateStore(kvStore).find("plugin-a").get().getRuntimeMode());

        service.markStopping("plugin-a", "reminder");
        PluginRuntimeState state = new PluginRuntimeStateStore(kvStore).find("plugin-a").get();
        assertEquals("stopping", state.getStatus());
        assertEquals("reminder", state.getPluginName());
    }

    @Test
    public void shouldFailFastWhenStarterThrows() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateService service = new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore), new FailingStarter());

        org.junit.Assert.assertFalse(service.ensureStarted("plugin-a"));

        PluginRuntimeState state = new PluginRuntimeStateStore(kvStore).find("plugin-a").get();
        assertEquals("failed", state.getStatus());
        assertEquals("missing file", state.getLastError());
    }

    @Test
    public void shouldAggregateMultipleRuntimeInstancesUnderSamePluginKey() {
        InMemoryRuntimeKvStore kvStore = new InMemoryRuntimeKvStore();
        PluginRuntimeStateStore store = new PluginRuntimeStateStore(kvStore);
        PluginRuntimeStateService first = new PluginRuntimeStateService(store, new FakeStarter(), "reminder-1");
        PluginRuntimeStateService second = new PluginRuntimeStateService(store, new FakeStarter(), "reminder-2");

        first.markReady("reminder", "待办提醒");
        second.markReady("reminder", "待办提醒");
        first.markInvocationStart("reminder", "待办提醒");

        PluginRuntimeState running = store.find("reminder").get();
        assertEquals("ready", running.getStatus());
        assertEquals(Integer.valueOf(1), running.getActiveInvocationCount());
        assertEquals(2, running.getInstances().size());

        first.markStopped("reminder", "待办提醒");

        PluginRuntimeState state = store.find("reminder").get();
        assertEquals("ready", state.getStatus());
        assertEquals(Integer.valueOf(0), state.getActiveInvocationCount());
        assertEquals(1, state.getInstances().size());
        assertEquals("reminder-2", state.getInstances().get(0).getInstanceId());
    }

    private static class FakeStarter implements PluginRuntimeStarter {

        private boolean started;
        private int startCount;

        @Override
        public boolean isStarted(String pluginId) {
            return started;
        }

        @Override
        public Optional<PluginIdentity> findPlugin(String pluginId) {
            return Optional.of(new PluginIdentity(pluginId, "reminder"));
        }

        @Override
        public void start(PluginIdentity identity) {
            startCount++;
            started = true;
        }
    }

    private static class FailingStarter implements PluginRuntimeStarter {

        @Override
        public boolean isStarted(String pluginId) {
            return false;
        }

        @Override
        public Optional<PluginIdentity> findPlugin(String pluginId) {
            return Optional.of(new PluginIdentity(pluginId, "reminder"));
        }

        @Override
        public void start(PluginIdentity identity) {
            throw new RuntimeException("missing file");
        }
    }

    private static class StartedStarter implements PluginRuntimeStarter {

        @Override
        public boolean isStarted(String pluginId) {
            return true;
        }

        @Override
        public Optional<PluginIdentity> findPlugin(String pluginId) {
            return Optional.of(new PluginIdentity(pluginId, "reminder"));
        }

        @Override
        public void start(PluginIdentity identity) {
        }
    }
}
