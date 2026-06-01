package com.zrlog.plugincore.server;

import java.io.IOException;

public class Application {

    static {
        ApplicationEnvironment.initStaticEnvironment();
    }

    public static void main(String[] args) throws IOException {
        new ApplicationStartup().start(args);
    }
}
