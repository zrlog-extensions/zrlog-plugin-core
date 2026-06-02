package com.zrlog.plugincore.server.util;

import com.zrlog.plugin.common.IOUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CmdUtil {

    public static String sendCmd(String cmd, String... args) {
        InputStream[] in = getCmdInputStream(cmd, args);
        if (in != null && in[0] != null) {
            return IOUtil.getStringInputStream(in[0]);
        }
        if (in != null && in[1] != null) {
            return IOUtil.getStringInputStream(in[1]);
        }
        return "";
    }

    public static InputStream[] getCmdInputStream(String cmd, String... args) {
        Process pr = getProcess(cmd, args);
        BufferedInputStream[] bufferedInputStreams = new BufferedInputStream[2];
        if (pr != null) {
            bufferedInputStreams[0] = new BufferedInputStream(pr.getInputStream());
            bufferedInputStreams[1] = new BufferedInputStream(pr.getErrorStream());
            return bufferedInputStreams;
        }
        return bufferedInputStreams;
    }

    public static Process getProcess(String cmd, Object... args) {
        if (args != null) {
            cmd += " ";
            StringBuilder cmdBuilder = new StringBuilder(cmd);
            for (Object str : args) {
                cmdBuilder.append(str).append(" ");
            }
            cmd = cmdBuilder.toString();
        }
        Runtime rt = Runtime.getRuntime();
        try {
            return rt.exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Process getProcess(File workingDirectory, Map<String, String> environment, String cmd, Object... args) {
        List<String> command = new ArrayList<>();
        command.add(cmd);
        if (args != null) {
            for (Object arg : args) {
                command.add(String.valueOf(arg));
            }
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            workingDirectory.mkdirs();
            processBuilder.directory(workingDirectory);
        }
        if (environment != null) {
            processBuilder.environment().putAll(environment);
        }
        try {
            return processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
