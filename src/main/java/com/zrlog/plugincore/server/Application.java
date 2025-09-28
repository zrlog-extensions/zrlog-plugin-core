package com.zrlog.plugincore.server;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.ParseArgsUtil;
import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.api.ISocketServer;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.common.ConfigKit;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.config.PluginHttpServerConfig;
import com.zrlog.plugincore.server.impl.NioServer;
import com.zrlog.plugincore.server.util.DevUtil;
import com.zrlog.plugincore.server.util.LambdaEnv;
import com.zrlog.plugincore.server.util.ListenWebServerThread;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.FileHandler;

public class Application {

    public static String BLOG_PLUGIN_TOKEN = "";
    public static String BLOG_API_HOME_URL = "http://localhost:7080";
    public static Boolean nativeAgent = false;
    public static String NATIVE_INFO = "";

    static {
        disableHikariLogging();
        LambdaEnv.initLambdaEnv();
    }

    private static void disableHikariLogging() {
        System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", "off");
    }

    public static void init() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %5$s%6$s%n");
        FileHandler fileHandler = com.hibegin.common.util.LoggerUtil.buildFileHandle();
        LoggerUtil.initFileHandle(fileHandler);
        com.hibegin.common.util.LoggerUtil.initFileHandle(fileHandler);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        init();
        if (Objects.nonNull(args) && ParseArgsUtil.justTips(args, "plugin-core", (String) ConfigKit.get("version", ""))) {
            return;
        }
        if (args != null && args.length > 0 && EnvKit.isDevMode()) {
            LoggerUtil.getLogger(Application.class).info("args = " + Arrays.toString(args));
        }
        if (Objects.isNull(args) || args.length == 0) {
            RunConstants.runType = RunType.DEV;
            System.getProperties().put("sws.run.mode", "dev");
        }
        Integer serverPort = (args != null && args.length > 0) ? Integer.parseInt(args[0]) : 9089;
        int masterPort = (args != null && args.length > 1) ? Integer.valueOf(args[1]) : ConfigKit.getServerPort();
        String dbProperties = (args != null && args.length > 2) ? args[2] : null;
        String pluginPath = (args != null && args.length > 3) ? args[3] : DevUtil.pluginHome();
        BlogRunTime blogRunTime = new BlogRunTime();
        if (dbProperties == null) {
            File tmpFile = File.createTempFile("blog-db", ".properties");
            dbProperties = tmpFile.toString();
            IOUtil.writeBytesToFile(IOUtil.getByteByInputStream(Application.class.getResourceAsStream("/db.properties")), tmpFile);
            blogRunTime.setPath(DevUtil.blogRuntimePath());
            blogRunTime.setVersion("1.5");
        } else {
            RunConstants.runType = EnvKit.isDevMode() ? RunType.DEV : RunType.BLOG;
            int port = (args.length > 4) ? Integer.parseInt(args[4]) : -1;
            blogRunTime.setPath((args.length > 5) ? args[5] : DevUtil.blogRuntimePath());
            blogRunTime.setVersion((args.length > 6) ? args[6] : DevUtil.blogVersion());
            if (args.length > 7) {
                BLOG_API_HOME_URL = "http://127.0.0.1:" + Integer.parseInt(args[7]);
            }
            BLOG_PLUGIN_TOKEN = (args.length > 8) ? args[8] : "_NOT_FOUND";
            NATIVE_INFO = (args.length > 9) ? args[9] : "";
            if (port > 0) {
                new ListenWebServerThread(port).start();
            }
            if (Objects.equals(NATIVE_INFO, "-")) {
                NATIVE_INFO = "";
            }
            //contextPath
            if (args.length > 10) {
                BLOG_API_HOME_URL = BLOG_API_HOME_URL + args[10].replace("#", "");
            }
        }
        //load Db
        PluginConfig.init(RunConstants.runType, new File(dbProperties), masterPort, pluginPath, blogRunTime);
        loadPluginServer(serverPort);
    }

    private static void loadPluginServer(Integer httpPort) {
        ISocketServer socketServer = new NioServer();
        if (!socketServer.create()) {
            return;
        }
        new Thread(socketServer::listen).start();
        loadHttpServer(httpPort);
    }

    private static void loadHttpServer(Integer serverPort) {
        PluginHttpServerConfig config = new PluginHttpServerConfig(serverPort);
        WebServerBuilder build = new WebServerBuilder.Builder().config(config).build();
        if (nativeAgent) {
            config.getServerConfig().addCreateSuccessHandle(() -> {
                Thread.sleep(5000);
                System.exit(0);
                return null;
            });
        }
        build.start();
    }
}