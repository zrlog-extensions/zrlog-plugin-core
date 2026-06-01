package com.zrlog.plugincore.server.web.controller.open;

import com.hibegin.http.server.web.Controller;
import com.zrlog.plugin.common.BasicCronParser;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.vo.PluginCoreSetting;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.capability.CapabilityStore;
import com.zrlog.plugincore.server.runtime.capability.RuntimeCapabilityInvokerFactory;
import com.zrlog.plugincore.server.runtime.capability.RuntimeSources;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationRunStore;
import com.zrlog.plugincore.server.runtime.scheduler.AutomationStore;
import com.zrlog.plugincore.server.runtime.scheduler.BearerSchedulerAuth;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerRuntime;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerTickResult;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerTickService;
import com.zrlog.plugincore.server.runtime.store.WebsiteRuntimeKvStore;

import java.time.ZonedDateTime;

public class SchedulerController extends Controller {

    public void tick() {
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        PluginCoreSetting setting = pluginCore.getSetting();
        setting.getScheduler().ensureDefaultProvider();
        if (!new BearerSchedulerAuth().verify(setting.getScheduler().getProviders(), request.getHeader("Authorization"))) {
            response.renderCode(403);
            return;
        }
        WebsiteRuntimeKvStore kvStore = new WebsiteRuntimeKvStore();
        SchedulerRuntime schedulerRuntime = new SchedulerRuntime(
                new AutomationStore(kvStore),
                new AutomationRunStore(kvStore),
                new CapabilityStore(kvStore),
                RuntimeCapabilityInvokerFactory.socket(kvStore, pluginCore),
                new BasicCronParser(),
                pluginCore
        );
        SchedulerTickResult result = new SchedulerTickService(setting.getScheduler(), schedulerRuntime).tick(ZonedDateTime.now(), RuntimeSources.TICK);
        response.renderJson(result);
    }
}
