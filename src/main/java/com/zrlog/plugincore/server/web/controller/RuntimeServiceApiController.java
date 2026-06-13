package com.zrlog.plugincore.server.web.controller;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.message.PluginCapability;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.dao.WebSiteDAO;
import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderResolver;
import com.zrlog.plugincore.server.runtime.service.ServiceProviderSetting;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.error;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.pluginsById;
import static com.zrlog.plugincore.server.web.controller.RuntimeApiResponses.success;
import static com.zrlog.plugincore.server.web.controller.RuntimeProviderResponses.*;

public class RuntimeServiceApiController extends RuntimeBaseApiController {

    private static final String COMMENT_PLUGIN_NAME_KEY = "comment_plugin_name";

    @ResponseBody
    public Map<String, Object> serviceProviders() {
        PluginCore pluginCore = PluginCoreDAO.getInstance().loadSnapshot();
        Map<String, Object> map = success();
        map.put("items", serviceProviderRows(
                capabilityStore().listByType("service"),
                pluginCore.getSetting().getService(),
                pluginsById(pluginCore)));
        return map;
    }

    @ResponseBody
    public Map<String, Object> serviceProviderUpdate() {
        String serviceName = getRequest().getParaToStr("serviceName");
        String pluginId = getRequest().getParaToStr("pluginId");
        String capabilityKey = getRequest().getParaToStr("capabilityKey");
        List<PluginCapability> serviceCapabilities = capabilityStore().listByType("service");
        PluginCapability provider = new ServiceProviderResolver().providersFor(serviceName, serviceCapabilities).stream()
                .filter(item -> Objects.equals(pluginId, item.getPluginId()))
                .filter(item -> Objects.equals(capabilityKey, item.getKey()))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            return error("服务能力不存在");
        }
        ServiceProviderSetting providerSetting = new ServiceProviderSetting();
        providerSetting.setServiceName(serviceName);
        providerSetting.setPluginId(pluginId);
        providerSetting.setCapabilityKey(capabilityKey);
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getService().getDefaultProviders().put(serviceName, providerSetting));
        return success();
    }

    @ResponseBody
    public Map<String, Object> serviceProviderAuto() {
        String serviceName = getRequest().getParaToStr("serviceName");
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getSetting().getService().getDefaultProviders().remove(serviceName));
        return success();
    }

    @ResponseBody
    public Map<String, Object> commentProviders() {
        try {
            List<Plugin> providers = commentProviderPlugins();
            String configured = commentPluginName();
            return commentProviderResponse(providers, configured);
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> commentProviderUpdate() {
        String shortName = getRequest().getParaToStr("shortName");
        if (findPluginByShortName(commentProviderPlugins(), shortName) == null) {
            return error("评论插件不存在");
        }
        try {
            new WebSiteDAO().saveOrUpdateChanged(COMMENT_PLUGIN_NAME_KEY, shortName);
            return success();
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    @ResponseBody
    public Map<String, Object> commentProviderDefault() {
        List<Plugin> providers = commentProviderPlugins();
        String shortName = findPluginByShortName(providers, DEFAULT_COMMENT_PLUGIN) == null && !providers.isEmpty()
                ? providers.get(0).getShortName()
                : DEFAULT_COMMENT_PLUGIN;
        try {
            new WebSiteDAO().saveOrUpdateChanged(COMMENT_PLUGIN_NAME_KEY, shortName);
            return success();
        } catch (SQLException e) {
            return error(e.getMessage());
        }
    }

    private String commentPluginName() throws SQLException {
        Object value = new WebSiteDAO().getWebSiteByNameIn(Collections.singletonList(COMMENT_PLUGIN_NAME_KEY)).get(COMMENT_PLUGIN_NAME_KEY);
        return value == null ? "" : value.toString();
    }
}
