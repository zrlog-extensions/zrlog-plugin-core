package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.IOUtil;
import com.zrlog.plugin.common.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ListenWebServerThread extends Thread {

    private static final Logger LOGGER = LoggerUtil.getLogger(ListenWebServerThread.class);

    private final int port;

    public ListenWebServerThread(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            // padding await the web server exception shutdown
            IOUtil.getByteByInputStream(inputStream);
            socket.close();
            serverSocket.close();
            System.exit(0);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }
}
