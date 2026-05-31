package com.zrlog.plugincore.server.runtime.scheduler;

import com.google.gson.Gson;
import com.zrlog.plugincore.server.runtime.InMemoryRuntimeKvStore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AutomationStoreTest {

    @Test
    public void shouldReturnDefaultDocumentWhenKvMissing() {
        AutomationStore store = new AutomationStore(new InMemoryRuntimeKvStore());

        assertEquals(0, store.list().size());
        assertEquals(AutomationStore.KEY, store.loadDocument().getSchema());
    }

    @Test
    public void shouldTrimAutomationRuns() {
        AutomationRunStore store = new AutomationRunStore(new InMemoryRuntimeKvStore());
        for (int i = 0; i < 501; i++) {
            PluginAutomationRun run = new PluginAutomationRun();
            run.setId("run-" + i);
            store.append(run);
        }

        assertEquals(500, store.list().size());
        assertTrue("run-1".equals(store.list().get(0).getId()));
    }

    @Test
    public void shouldRetryAutomationRunAppendWhenConcurrentWriteWinsFirst() {
        StaleAutomationRunKvStore kvStore = new StaleAutomationRunKvStore(documentJson(run("external")));
        AutomationRunStore store = new AutomationRunStore(kvStore);

        store.append(run("local"));

        assertEquals(2, store.list().size());
        assertTrue(store.list().stream().anyMatch(item -> "external".equals(item.getId())));
        assertTrue(store.list().stream().anyMatch(item -> "local".equals(item.getId())));
        assertEquals(2, kvStore.getAutomationRunCompareAndSetCount());
    }

    private PluginAutomationRun run(String id) {
        PluginAutomationRun run = new PluginAutomationRun();
        run.setId(id);
        return run;
    }

    private String documentJson(PluginAutomationRun... runs) {
        AutomationRunDocument document = new AutomationRunDocument();
        document.setItems(Arrays.asList(runs));
        return new Gson().toJson(document);
    }

    private static class StaleAutomationRunKvStore extends InMemoryRuntimeKvStore {
        private final String staleValue;
        private boolean staleInjected;
        private int automationRunCompareAndSetCount;

        private StaleAutomationRunKvStore(String staleValue) {
            this.staleValue = staleValue;
        }

        @Override
        public synchronized boolean compareAndSet(String key, Optional<String> expectedValue, String value) {
            if (!AutomationRunStore.KEY.equals(key)) {
                return super.compareAndSet(key, expectedValue, value);
            }
            automationRunCompareAndSetCount++;
            if (!staleInjected) {
                put(key, staleValue);
                staleInjected = true;
                return false;
            }
            return super.compareAndSet(key, expectedValue, value);
        }

        private int getAutomationRunCompareAndSetCount() {
            return automationRunCompareAndSetCount;
        }
    }
}
