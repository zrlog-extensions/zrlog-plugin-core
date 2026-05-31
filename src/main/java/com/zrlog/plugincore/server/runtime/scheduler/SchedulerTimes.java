package com.zrlog.plugincore.server.runtime.scheduler;

import com.zrlog.plugin.common.BasicCronParser;

import java.time.ZoneId;
import java.time.ZonedDateTime;

final class SchedulerTimes {

    private SchedulerTimes() {
    }

    static long millis(ZonedDateTime time) {
        return time.toInstant().toEpochMilli();
    }

    static long nextRunAtMillis(BasicCronParser cronParser, String cron, ZoneId zoneId, ZonedDateTime now) {
        ZonedDateTime baseTime = now == null ? ZonedDateTime.now(zoneId) : now.withZoneSameInstant(zoneId);
        return millis(cronParser.nextRunAt(cron, zoneId, baseTime));
    }
}
