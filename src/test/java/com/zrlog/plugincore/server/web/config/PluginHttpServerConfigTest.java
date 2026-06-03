package com.zrlog.plugincore.server.web.config;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.web.Router;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.runtime.PluginRuntimeServices;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;
import com.zrlog.plugincore.server.runtime.scheduler.SchedulerExternalEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PluginHttpServerConfigTest {

    private RunType originalRunType;

    @Before
    public void setUp() {
        originalRunType = RunConstants.runType;
    }

    @After
    public void tearDown() {
        RunConstants.runType = originalRunType;
        PluginRuntimeBridge.install(PluginRuntimeServices.unconfigured());
    }

    @Test
    public void shouldRegisterRootAndAdminPluginRuntimeRoutesInBlogMode() {
        RunConstants.runType = RunType.BLOG;
        Router router = new PluginHttpServerConfig(0).getServerConfig().getRouter();

        assertRuntimePages(router, "");
        assertRuntimeApis(router, "/api");
        assertRuntimePages(router, "/admin/plugins");
        assertRuntimeApis(router, "/admin/plugins/api");
        assertLegacyNestedRuntimePathsMissing(router, "");
        assertLegacyNestedRuntimePathsMissing(router, "/admin/plugins");
    }

    @Test
    public void shouldRegisterRootAndAdminPluginRuntimeRoutesInDevMode() {
        RunConstants.runType = RunType.DEV;
        Router router = new PluginHttpServerConfig(0).getServerConfig().getRouter();

        assertRuntimePages(router, "");
        assertRuntimeApis(router, "/api");
        assertRuntimePages(router, "/admin/plugins");
        assertRuntimeApis(router, "/admin/plugins/api");
        assertLegacyNestedRuntimePathsMissing(router, "");
        assertLegacyNestedRuntimePathsMissing(router, "/admin/plugins");
    }

    @Test
    public void shouldRegisterExternalSchedulerTickPathOnce() {
        RunConstants.runType = RunType.BLOG;
        Router router = new PluginHttpServerConfig(0).getServerConfig().getRouter();

        assertRoute(router, SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH, HttpMethod.POST);
        assertMissing(router, "/admin/plugins" + SchedulerExternalEndpoint.EXTERNAL_TICK_EXPOSE_PATH, HttpMethod.POST);
    }

    @Test
    public void shouldBindRuntimeServicesForWebServer() {
        PluginRuntimeServices services = PluginRuntimeServices.unconfigured();

        new PluginHttpServerConfig(0, services);

        assertSame(services.pluginBootstrap(), PluginRuntimeBridge.pluginBootstrap());
        assertSame(services.pluginConfig(), PluginRuntimeBridge.pluginConfig());
    }

    private void assertRuntimePages(Router router, String prefix) {
        assertRoute(router, prefix + "/runtime-scheduler", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-scheduler/runs", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-scheduler/settings", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-states", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-notification", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-services", HttpMethod.GET);
    }

    private void assertRuntimeApis(Router router, String prefix) {
        assertRoute(router, prefix + "/runtime-scheduler/settings", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-scheduler/settings", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-scheduler/tick", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-automations", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-automations", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-automations/update", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-automations/run", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-automations/delete", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-automation-runs", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-capabilities", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-states", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-states/start", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-states/stop", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-settings", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-settings", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-invocation-logs", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-notification/channels", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-notification/provider", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-notification/provider/auto", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-notification/test", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-notification/deliveries", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-services/providers", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-services/provider", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-services/provider/auto", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-services/comment-providers", HttpMethod.GET);
        assertRoute(router, prefix + "/runtime-services/comment-provider", HttpMethod.POST);
        assertRoute(router, prefix + "/runtime-services/comment-provider/default", HttpMethod.POST);
    }

    private void assertMissingRuntimePages(Router router, String prefix) {
        assertMissing(router, prefix + "/runtime-scheduler", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-states", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-notification", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-services", HttpMethod.GET);
    }

    private void assertMissingRuntimeApis(Router router, String prefix) {
        assertMissing(router, prefix + "/runtime-automations", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-scheduler/settings", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-states", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime-services/providers", HttpMethod.GET);
    }

    private void assertLegacyNestedRuntimePathsMissing(Router router, String prefix) {
        assertMissing(router, prefix + "/runtime/scheduler", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime/states", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime/notification", HttpMethod.GET);
        assertMissing(router, prefix + "/runtime/services", HttpMethod.GET);
        assertMissing(router, prefix + "/api/runtime/automations", HttpMethod.GET);
        assertMissing(router, prefix + "/api/runtime/scheduler/settings", HttpMethod.GET);
    }

    private void assertRoute(Router router, String path, HttpMethod method) {
        assertNotNull(path, router.getMethod(path, method));
    }

    private void assertMissing(Router router, String path, HttpMethod method) {
        assertNull(path, router.getMethod(path, method));
    }
}
