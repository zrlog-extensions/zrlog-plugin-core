package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugin.common.CronParseException;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BasicCronParserTest {

    private final BasicCronParser parser = new BasicCronParser();

    @Test
    public void shouldCalculateStepMinuteCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 29, 10, 1, 30, 0, ZoneId.of("Asia/Shanghai"));

        ZonedDateTime next = parser.nextRunAt("*/5 * * * *", ZoneId.of("Asia/Shanghai"), now);

        assertEquals(ZonedDateTime.of(2026, 5, 29, 10, 5, 0, 0, ZoneId.of("Asia/Shanghai")), next);
    }

    @Test
    public void shouldCalculateSingleNumberCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 29, 2, 59, 0, 0, ZoneId.of("Asia/Shanghai"));

        ZonedDateTime next = parser.nextRunAt("0 3 * * *", ZoneId.of("Asia/Shanghai"), now);

        assertEquals(ZonedDateTime.of(2026, 5, 29, 3, 0, 0, 0, ZoneId.of("Asia/Shanghai")), next);
    }

    @Test
    public void shouldUseSystemTimezoneWhenZoneIsMissing() {
        ZonedDateTime next = parser.nextRunAt("*/1 * * * *", null, null);

        assertNotNull(next);
        assertEquals(ZoneId.systemDefault(), next.getZone());
    }

    @Test(expected = CronParseException.class)
    public void shouldRejectComplexCron() {
        parser.nextRunAt("0 0 L * *", ZoneId.of("Asia/Shanghai"), ZonedDateTime.now());
    }
}
