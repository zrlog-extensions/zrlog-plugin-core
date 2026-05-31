package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.CronParseException;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.capability.CapabilityInvoker;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.InvokeContext;
import com.zrlog.plugincore.server.runtime.lock.DistributedLock;
import com.zrlog.plugincore.server.runtime.state.PluginIdleStopRunner;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeSetting;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class SchedulerRuntime {

    private static final Map<String, Semaphore> EXECUTION_LOCKS = new ConcurrentHashMap<>();
    private static final int STORE_UPDATE_RETRIES = 3;
    private static final int MAX_TICK_TASK_THREADS = 8;
    private static final long LEASE_SECONDS = 300L;
    private static final String LOCK_KEY_PREFIX = "plugin-scheduler-";

    private final AutomationStore automationStore;
    private final AutomationRunStore automationRunStore;
    private final CapabilityInvoker capabilityInvoker;
    private final BasicCronParser cronParser;
    private final SchedulerTaskLockFactory taskLockFactory;
    private final Supplier<PluginRuntimeSetting> runtimeSettingSupplier;

    public SchedulerRuntime(AutomationStore automationStore,
                            AutomationRunStore automationRunStore,
                            CapabilityStore capabilityStore,
                            CapabilityInvoker capabilityInvoker,
                            BasicCronParser cronParser) {
        this(automationStore, automationRunStore, capabilityStore, capabilityInvoker, cronParser, SchedulerRuntime::distributedTaskLock);
    }

    SchedulerRuntime(AutomationStore automationStore,
                     AutomationRunStore automationRunStore,
                     CapabilityStore capabilityStore,
                     CapabilityInvoker capabilityInvoker,
                     BasicCronParser cronParser,
                     SchedulerTaskLockFactory taskLockFactory) {
        this(automationStore, automationRunStore, capabilityStore, capabilityInvoker, cronParser, taskLockFactory,
                () -> PluginCoreDAO.getInstance().loadSnapshot().getSetting().getRuntime());
    }

    SchedulerRuntime(AutomationStore automationStore,
                     AutomationRunStore automationRunStore,
                     CapabilityStore capabilityStore,
                     CapabilityInvoker capabilityInvoker,
                     BasicCronParser cronParser,
                     SchedulerTaskLockFactory taskLockFactory,
                     Supplier<PluginRuntimeSetting> runtimeSettingSupplier) {
        this.automationStore = automationStore;
        this.automationRunStore = automationRunStore;
        this.capabilityInvoker = capabilityInvoker;
        this.cronParser = cronParser;
        this.taskLockFactory = taskLockFactory;
        this.runtimeSettingSupplier = runtimeSettingSupplier;
    }

    public SchedulerTickResult tick(ZonedDateTime now) {
        ensureSystemAutomations(now);
        SchedulerTickResult result = new SchedulerTickResult();
        List<PluginAutomation> automations = automationStore.list();
        List<ClaimedAutomation> claimedAutomations = new ArrayList<>();
        for (PluginAutomation automation : automations) {
            if (!Boolean.TRUE.equals(automation.getEnabled())) {
                result.skipped();
                continue;
            }
            Semaphore lock = executionLock(automation);
            if (!lock.tryAcquire()) {
                result.skipped();
                continue;
            }
            SchedulerTaskLock taskLock = taskLockFactory.create(executionKey(automation));
            if (!taskLock.tryLock()) {
                lock.release();
                result.skipped();
                continue;
            }
            boolean claimed = false;
            try {
                PluginAutomation claimedAutomation = claimDueAutomation(automation, now, UUID.randomUUID().toString());
                if (claimedAutomation == null) {
                    result.skipped();
                    continue;
                }
                claimedAutomations.add(new ClaimedAutomation(claimedAutomation, lock, taskLock));
                claimed = true;
            } finally {
                if (!claimed) {
                    try {
                        taskLock.unlock();
                    } finally {
                        lock.release();
                    }
                }
            }
        }
        if (claimedAutomations.isEmpty()) {
            return result;
        }
        ExecutorService executorService = Executors.newFixedThreadPool(
                Math.min(MAX_TICK_TASK_THREADS, claimedAutomations.size()),
                runnable -> {
                    Thread thread = new Thread(runnable, "zrlog-plugin-scheduler-task");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            List<CompletableFuture<SchedulerTickResult>> futures = new ArrayList<>();
            for (ClaimedAutomation claimedAutomation : claimedAutomations) {
                futures.add(CompletableFuture.supplyAsync(() -> executeClaimedAutomation(claimedAutomation, now), executorService));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<SchedulerTickResult> future : futures) {
                result.merge(future.join());
            }
        } finally {
            executorService.shutdown();
        }
        return result;
    }

    private void ensureSystemAutomations(ZonedDateTime now) {
        PluginRuntimeSetting runtimeSetting = runtimeSettingSupplier.get();
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            AutomationStore.AutomationDocumentSnapshot snapshot = automationStore.loadSnapshot();
            List<PluginAutomation> automations = new ArrayList<>(snapshot.getDocument().getItems());
            boolean changed = RuntimeSystemAutomations.ensureRuntimeMaintenance(automations, runtimeSetting, cronParser, now);
            if (!changed) {
                return;
            }
            snapshot.getDocument().setItems(automations);
            if (automationStore.saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
        throw new IllegalStateException("Failed to ensure scheduler system automations due to concurrent modification");
    }

    private SchedulerTickResult executeClaimedAutomation(ClaimedAutomation claimedAutomation, ZonedDateTime now) {
        SchedulerTickResult result = new SchedulerTickResult();
        try {
            PluginAutomationRun run = executeAutomation(claimedAutomation.getAutomation(), now);
            automationRunStore.append(run);
            if (Objects.equals("success", run.getStatus())) {
                result.executed();
            } else {
                result.failed();
            }
        } catch (RuntimeException e) {
            result.failed();
        } finally {
            try {
                finishClaimedAutomation(claimedAutomation.getAutomation(), now, false);
            } finally {
                try {
                    claimedAutomation.getTaskLock().unlock();
                } finally {
                    claimedAutomation.getLock().release();
                }
            }
        }
        return result;
    }

    public PluginAutomationRun runNow(String automationId, ZonedDateTime now) {
        if (automationId == null || automationId.trim().isEmpty()) {
            throw new CronParseException("Automation id is empty");
        }
        List<PluginAutomation> automations = automationStore.list();
        for (PluginAutomation automation : automations) {
            if (!Objects.equals(automationId, automation.getId())) {
                continue;
            }
            Semaphore lock = executionLock(automation);
            if (!lock.tryAcquire()) {
                throw new CronParseException("Automation is already running");
            }
            try {
                PluginAutomation claimed = claimAutomation(automation, now, UUID.randomUUID().toString(), true);
                if (claimed == null) {
                    throw new CronParseException("Automation is already running");
                }
                PluginAutomationRun run = executeAutomation(claimed, now);
                automationRunStore.append(run);
                finishClaimedAutomation(claimed, now, true);
                return run;
            } finally {
                lock.release();
            }
        }
        throw new CronParseException("Automation not found");
    }

    private Semaphore executionLock(PluginAutomation automation) {
        // Local semaphore gates this JVM; DistributedLock gates duplicate scheduler tasks across processes.
        return EXECUTION_LOCKS.computeIfAbsent(executionKey(automation), key -> new Semaphore(1));
    }

    private String executionKey(PluginAutomation automation) {
        return automation.getPluginId() + ":" + automation.getCapabilityKey();
    }

    private static SchedulerTaskLock distributedTaskLock(String key) {
        DistributedLock lock = new DistributedLock(LOCK_KEY_PREFIX + key);
        return new SchedulerTaskLock() {
            @Override
            public boolean tryLock() {
                return lock.tryLock();
            }

            @Override
            public void unlock() {
                lock.unlock();
            }
        };
    }

    private PluginAutomation claimDueAutomation(PluginAutomation automation, ZonedDateTime now, String leaseOwner) {
        return claimAutomation(automation, now, leaseOwner, false);
    }

    private PluginAutomation claimAutomation(PluginAutomation automation, ZonedDateTime now, String leaseOwner, boolean ignoreSchedule) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            AutomationStore.AutomationDocumentSnapshot snapshot = automationStore.loadSnapshot();
            PluginAutomation current = findAutomation(snapshot.getDocument().getItems(), automation);
            if (current == null || !Boolean.TRUE.equals(current.getEnabled())) {
                return null;
            }
            if (!ignoreSchedule) {
                if (current.getNextRunAt() == null) {
                    updateNextRunAt(current, now);
                    if (automationStore.saveDocumentIfUnchanged(snapshot)) {
                        return null;
                    }
                    continue;
                }
                if (current.getNextRunAt() > SchedulerTimes.millis(now)) {
                    return null;
                }
            }
            if (leaseActive(current, now)) {
                return null;
            }
            current.setLeaseOwner(leaseOwner);
            current.setLeaseUntil(now.plusSeconds(LEASE_SECONDS).toString());
            if (automationStore.saveDocumentIfUnchanged(snapshot)) {
                return current;
            }
        }
        return null;
    }

    private void finishClaimedAutomation(PluginAutomation automation, ZonedDateTime now, boolean keepFutureNextRunAt) {
        for (int i = 0; i < STORE_UPDATE_RETRIES; i++) {
            AutomationStore.AutomationDocumentSnapshot snapshot = automationStore.loadSnapshot();
            PluginAutomation current = findAutomation(snapshot.getDocument().getItems(), automation);
            if (current == null || !Objects.equals(automation.getLeaseOwner(), current.getLeaseOwner())) {
                return;
            }
            current.setLastRunAt(SchedulerTimes.millis(now));
            if (!keepFutureNextRunAt || current.getNextRunAt() == null) {
                updateNextRunAt(current, now);
            }
            clearLease(current);
            if (automationStore.saveDocumentIfUnchanged(snapshot)) {
                return;
            }
        }
    }

    private PluginAutomation findAutomation(List<PluginAutomation> automations, PluginAutomation target) {
        for (PluginAutomation automation : automations) {
            if (sameAutomation(automation, target)) {
                return automation;
            }
        }
        return null;
    }

    private boolean sameAutomation(PluginAutomation left, PluginAutomation right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return Objects.equals(left.getPluginId(), right.getPluginId())
                && Objects.equals(left.getCapabilityKey(), right.getCapabilityKey());
    }

    private boolean leaseActive(PluginAutomation automation, ZonedDateTime now) {
        if (automation.getLeaseOwner() == null || automation.getLeaseOwner().trim().isEmpty()
                || automation.getLeaseUntil() == null || automation.getLeaseUntil().trim().isEmpty()) {
            return false;
        }
        return ZonedDateTime.parse(automation.getLeaseUntil()).isAfter(now);
    }

    private void clearLease(PluginAutomation automation) {
        automation.setLeaseOwner(null);
        automation.setLeaseUntil(null);
    }

    private PluginAutomationRun executeAutomation(PluginAutomation automation, ZonedDateTime now) {
        PluginAutomationRun run = newRun(automation, now);
        if (RuntimeSystemAutomations.isRuntimeMaintenance(automation)) {
            executeRuntimeMaintenance(automation, now);
            run.setStatus("success");
            return finish(run, now);
        }
        InvokeContext context = new InvokeContext();
        context.setSource("scheduler");
        context.setRequestId(UUID.randomUUID().toString());
        context.setAuditRequired(true);
        CapabilityInvokeResult invokeResult = capabilityInvoker.invoke(automation.getPluginId(), automation.getCapabilityKey(), automation.getPayload(), context);
        if (invokeResult.isSuccess()) {
            run.setStatus("success");
        } else {
            run.setStatus("error");
            run.setErrorMessage(invokeResult.getErrorMessage());
        }
        return finish(run, now);
    }

    private void executeRuntimeMaintenance(PluginAutomation automation, ZonedDateTime now) {
        PluginRuntimeStates.cleanupDirtyRuntimeStates(PluginCoreDAO.getInstance().loadSnapshot());
        PluginRuntimeSetting runtimeSetting = RuntimeSystemAutomations.runtimeSettingFromPayload(automation.getPayload());
        if (!runtimeSetting.getOnDemandEnabled()) {
            com.zrlog.plugincore.server.plugin.PluginBootstrap.loadPluginsAsync();
        }
        if (runtimeSetting.getIdleStopEnabled()) {
            new PluginIdleStopRunner().stopIdlePlugins(now.toInstant().toEpochMilli(), runtimeSetting);
        }
    }

    private PluginAutomationRun newRun(PluginAutomation automation, ZonedDateTime now) {
        PluginAutomationRun run = new PluginAutomationRun();
        run.setId(UUID.randomUUID().toString());
        run.setAutomationId(automation.getId());
        run.setPluginId(automation.getPluginId());
        run.setCapabilityKey(automation.getCapabilityKey());
        run.setStartedAt(SchedulerTimes.millis(now));
        return run;
    }

    private PluginAutomationRun finish(PluginAutomationRun run, ZonedDateTime now) {
        long finishedAt = SchedulerTimes.millis(now);
        run.setFinishedAt(finishedAt);
        run.setDurationMs(Math.max(0L, finishedAt - run.getStartedAt()));
        return run;
    }

    private void updateNextRunAt(PluginAutomation automation, ZonedDateTime now) {
        ZoneId zoneId = resolveZone(automation.getTimezone());
        automation.setTimezone(zoneId.getId());
        automation.setNextRunAt(SchedulerTimes.nextRunAtMillis(cronParser, automation.getCron(), zoneId, now));
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty() || Objects.equals("system", timezone)) {
            return ZoneId.systemDefault();
        }
        return ZoneId.of(timezone);
    }

    private static class ClaimedAutomation {
        private final PluginAutomation automation;
        private final Semaphore lock;
        private final SchedulerTaskLock taskLock;

        private ClaimedAutomation(PluginAutomation automation, Semaphore lock, SchedulerTaskLock taskLock) {
            this.automation = automation;
            this.lock = lock;
            this.taskLock = taskLock;
        }

        public PluginAutomation getAutomation() {
            return automation;
        }

        public Semaphore getLock() {
            return lock;
        }

        public SchedulerTaskLock getTaskLock() {
            return taskLock;
        }
    }

    interface SchedulerTaskLockFactory {
        SchedulerTaskLock create(String key);
    }

    interface SchedulerTaskLock {
        boolean tryLock();

        void unlock();
    }
}
