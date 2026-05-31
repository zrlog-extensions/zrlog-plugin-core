package com.zrlog.plugincore.server.runtime.service;

import com.zrlog.plugin.message.PluginCapability;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ServiceProviderResolverTest {

    @Test
    public void resolveUsesConfiguredProviderForSameLegacyService() {
        PluginCapability qiniu = provider("qiniu-id", "qiniu.upload", "uploadService", "七牛云存储");
        PluginCapability oss = provider("oss-id", "oss.upload", "uploadService", "阿里云 OSS");

        ServiceSetting setting = new ServiceSetting();
        ServiceProviderSetting providerSetting = new ServiceProviderSetting();
        providerSetting.setServiceName("uploadService");
        providerSetting.setPluginId("oss-id");
        providerSetting.setCapabilityKey("oss.upload");
        setting.getDefaultProviders().put("uploadService", providerSetting);

        PluginCapability resolved = new ServiceProviderResolver()
                .resolve("uploadService", Arrays.asList(qiniu, oss), setting)
                .orElse(null);

        Assert.assertNotNull(resolved);
        Assert.assertEquals("oss-id", resolved.getPluginId());
        Assert.assertFalse(new ServiceProviderResolver().reviewRequired("uploadService", Arrays.asList(qiniu, oss), setting));
    }

    @Test
    public void resolveRequiresReviewWhenMultipleProvidersAreUnconfigured() {
        PluginCapability qiniu = provider("qiniu-id", "qiniu.upload", "uploadService", "七牛云存储");
        PluginCapability oss = provider("oss-id", "oss.upload", "uploadService", "阿里云 OSS");

        Assert.assertTrue(new ServiceProviderResolver()
                .reviewRequired("uploadService", Arrays.asList(qiniu, oss), new ServiceSetting()));
    }

    @Test
    public void resolveDoesNotMixDifferentServiceAliases() {
        PluginCapability upload = provider("cos-id", "cos.upload", "uploadService", "腾讯云 COS");
        PluginCapability privateUpload = provider("cos-id", "cos.uploadPrivate", "uploadToPrivateService", "腾讯云 COS");

        PluginCapability resolved = new ServiceProviderResolver()
                .resolve("uploadToPrivateService", Arrays.asList(upload, privateUpload), new ServiceSetting())
                .orElse(null);

        Assert.assertNotNull(resolved);
        Assert.assertEquals("cos.uploadPrivate", resolved.getKey());
    }

    @Test
    public void resolveSupportsPersistedOldUploadCapabilityWithoutServiceName() {
        PluginCapability qiniu = provider("qiniu-id", "qiniu.upload", null, "七牛云存储");

        PluginCapability resolved = new ServiceProviderResolver()
                .resolve("uploadService", Arrays.asList(qiniu), new ServiceSetting())
                .orElse(null);

        Assert.assertNotNull(resolved);
        Assert.assertEquals("qiniu.upload", resolved.getKey());
    }

    private PluginCapability provider(String pluginId, String key, String serviceName, String pluginName) {
        PluginCapability capability = new PluginCapability();
        capability.setPluginId(pluginId);
        capability.setPluginName(pluginName);
        capability.setKey(key);
        capability.setServiceName(serviceName);
        capability.setType("service");
        capability.setExposure(Arrays.asList("internal"));
        capability.setEnabled(Boolean.TRUE);
        return capability;
    }
}
