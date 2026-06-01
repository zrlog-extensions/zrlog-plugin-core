package com.zrlog.plugincore.server.runtime.capability;

import com.zrlog.plugincore.server.model.PluginCore;
import com.zrlog.plugincore.server.runtime.invocation.InvocationLogStore;
import com.zrlog.plugincore.server.runtime.state.DefaultPluginRuntimeStarter;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateService;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStateStore;
import com.zrlog.plugin.common.KvRepository;

public class RuntimeCapabilityInvokerFactory {

    private RuntimeCapabilityInvokerFactory() {
    }

    public static CapabilityInvoker socket(KvRepository kvStore) {
        return socket(kvStore, null);
    }

    public static CapabilityInvoker socket(KvRepository kvStore, PluginCore pluginCore) {
        CapabilityStore capabilityStore = new CapabilityStore(kvStore);
        return new TrackingCapabilityInvoker(
                new SocketCapabilityInvoker(),
                new PluginRuntimeStateService(new PluginRuntimeStateStore(kvStore),
                        pluginCore == null ? new DefaultPluginRuntimeStarter() : new DefaultPluginRuntimeStarter(pluginCore)),
                new InvocationLogStore(kvStore),
                capabilityStore
        );
    }
}
