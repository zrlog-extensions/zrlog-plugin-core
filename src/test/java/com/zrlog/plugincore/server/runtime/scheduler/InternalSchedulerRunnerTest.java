package com.zrlog.plugincore.server.runtime.scheduler;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class InternalSchedulerRunnerTest {

    @Test
    public void shouldAlignNextTickToMinuteBoundary() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");

        assertEquals(60000L, InternalSchedulerRunner.millisUntilNextTick(
                ZonedDateTime.of(2026, 5, 31, 16, 1, 0, 0, zoneId)));
        assertEquals(30000L, InternalSchedulerRunner.millisUntilNextTick(
                ZonedDateTime.of(2026, 5, 31, 16, 1, 30, 0, zoneId)));
        assertEquals(1L, InternalSchedulerRunner.millisUntilNextTick(
                ZonedDateTime.of(2026, 5, 31, 16, 1, 59, 999_000_000, zoneId)));
    }
}
