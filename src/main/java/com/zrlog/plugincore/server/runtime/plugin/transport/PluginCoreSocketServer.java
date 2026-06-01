package com.zrlog.plugincore.server.runtime.plugin.transport;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.ISocketServer;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.data.codec.SocketCodec;
import com.zrlog.plugin.data.codec.SocketDecode;
import com.zrlog.plugin.data.codec.SocketEncode;
import com.zrlog.plugincore.server.runtime.plugin.bootstrap.PluginBootstrapService;
import com.zrlog.plugincore.server.runtime.plugin.config.PluginConfig;
import com.zrlog.plugincore.server.runtime.PluginRuntimeBridge;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginCoreSocketServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreSocketServer.class);

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private int port;
    private final Map<Socket, IOSession> decoderMap = new ConcurrentHashMap<>();
    private final Executor executor = Executors.newFixedThreadPool(8);
    private final PluginConfig pluginConfig;
    private final PluginBootstrapService pluginBootstrap;

    public PluginCoreSocketServer() {
        this(PluginRuntimeBridge.pluginConfig(), PluginRuntimeBridge.pluginBootstrap());
    }

    public PluginCoreSocketServer(PluginConfig pluginConfig, PluginBootstrapService pluginBootstrap) {
        this.pluginConfig = pluginConfig;
        this.pluginBootstrap = pluginBootstrap;
    }

    @Override
    public void listen() {
        if (selector == null) {
            return;
        }
        while (selector.isOpen()) {
            try {
                if (selector.select(200) <= 0) {
                    continue;
                }
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    SocketChannel channel;
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        channel = server.accept();
                        if (channel != null) {
                            channel.configureBlocking(false);
                            channel.register(selector, SelectionKey.OP_READ);
                        }
                    } else if (key.isReadable()) {
                        channel = (SocketChannel) key.channel();
                        if (channel != null) {
                            IOSession session = decoderMap.get(channel.socket());
                            if (session == null) {
                                session = new IOSession(channel, selector, new SocketCodec(new SocketEncode(), new SocketDecode(executor)), new ServerActionHandler());
                                decoderMap.put(channel.socket(), session);
                            }
                            dispose(session, channel, key);
                        }
                    }
                    iter.remove();
                }
            } catch (Exception e) {
                if (e instanceof ClosedSelectorException) {
                    return;
                }
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    @Override
    public void destroy(String s) {
        closeQuietly(selector);
        closeQuietly(serverChannel);
    }

    @Override
    public boolean create() {
        return create(pluginConfig.getMasterPort());
    }

    @Override
    public boolean create(int port) {
        return create("127.0.0.1", port);
    }

    @Override
    public boolean create(String s, int port) {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(s, port));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            this.port = port;

            LOGGER.info("zrlog-plugin-core-server listening on port -> " + port);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return false;
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "close plugin socket server error", e);
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    private void dispose(IOSession session, SocketChannel channel, SelectionKey key) {
        long start = System.currentTimeMillis();
        SocketDecode decode = (SocketDecode) session.getSystemAttr().get("_decode");
        try {
            while (!decode.doDecode(session)) {
                Thread.sleep(100);
            }
        } catch (Exception e) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "close channel error " + e.getMessage());
            }
            pluginBootstrap().unregisterPluginSession(session);
            LOGGER.log(Level.SEVERE, "dispose error " + e.getMessage());
        } finally {
            if (EnvKit.isDevMode()) {
                LOGGER.info("doDecode used time " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }

    private PluginBootstrapService pluginBootstrap() {
        return pluginBootstrap;
    }
}
