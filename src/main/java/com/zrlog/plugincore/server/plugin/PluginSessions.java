package com.zrlog.plugincore.server.plugin;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugincore.server.config.PluginVO;
import com.zrlog.plugincore.server.dao.PluginCoreDAO;
import com.zrlog.plugincore.server.runtime.state.PluginRuntimeStates;
import com.zrlog.plugincore.server.util.StringUtils;

import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class PluginSessions {

    private static final String SESSION_ID_ATTR = "_zrlog_session_id";
    private static final String PROCESS_ID_ATTR = "_zrlog_process_id";
    private static final List<IOSession> LOCAL_SESSIONS = new CopyOnWriteArrayList<>();

    private PluginSessions() {
    }

    static void registerPlugin(IOSession session, PluginScanRunnable pluginScanRunnable) {
        if (Objects.isNull(session) || session.getPlugin() == null) {
            return;
        }
        closeDuplicatePluginInstances(session, pluginScanRunnable);
        attachProcessInfo(session, pluginScanRunnable);
        PluginVO pluginVO = new PluginVO();
        pluginVO.setPlugin(session.getPlugin());
        pluginVO.setFileMd5(PluginFiles.pluginFileMd5(PluginFiles.getPluginFile(session.getPlugin().getShortName())));
        PluginCoreDAO.getInstance().update(pluginCore ->
                pluginCore.getPluginInfoMap().put(session.getPlugin().getShortName(), pluginVO));
        registerLocalSession(session);
    }

    static void unregisterPluginSession(IOSession session) {
        if (session == null || session.getPlugin() == null) {
            return;
        }
        String pluginId = session.getPlugin().getId();
        String runtimeInstanceId = runtimeInstanceId(session);
        unregisterLocalSession(session);
        if (!hasOpenSessionForRuntimeInstance(pluginId, runtimeInstanceId)) {
            PluginRuntimeStates.markStoppedIfCurrent(session);
        }
    }

    private static void closeDuplicatePluginInstances(IOSession session, PluginScanRunnable pluginScanRunnable) {
        if (session.getPlugin() == null || StringUtils.isEmpty(session.getPlugin().getShortName())) {
            return;
        }
        String shortName = session.getPlugin().getShortName();
        String currentPluginId = session.getPlugin().getId();
        Set<String> stalePluginIds = new HashSet<>();
        PluginVO existingPluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(shortName);
        if (existingPluginVO != null && existingPluginVO.getPlugin() != null
                && !Objects.equals(currentPluginId, existingPluginVO.getPlugin().getId())) {
            stalePluginIds.add(existingPluginVO.getPlugin().getId());
        }
        for (IOSession oldSession : getAllLocalSessions()) {
            if (oldSession.getPlugin() == null || !Objects.equals(shortName, oldSession.getPlugin().getShortName())
                    || Objects.equals(currentPluginId, oldSession.getPlugin().getId())) {
                continue;
            }
            stalePluginIds.add(oldSession.getPlugin().getId());
        }
        for (String stalePluginId : stalePluginIds) {
            if (pluginScanRunnable != null) {
                pluginScanRunnable.destroyByPluginId(stalePluginId, shortName);
            } else {
                if (closeLocalSessionsByPluginId(stalePluginId).isEmpty()) {
                    PluginRuntimeStates.markStoppedByPluginId(stalePluginId, shortName);
                }
            }
        }
    }

    public static boolean isCurrentPluginIdentity(Plugin plugin) {
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(plugin.getShortName());
        return pluginVO != null && pluginVO.getPlugin() != null
                && Objects.equals(plugin.getId(), pluginVO.getPlugin().getId());
    }

    public static String nameOrShortName(Plugin plugin) {
        if (plugin == null) {
            return "";
        }
        if (!StringUtils.isEmpty(plugin.getName())) {
            return plugin.getName();
        }
        return plugin.getShortName();
    }


    public static List<Plugin> allPlugins() {
        List<Plugin> allPlugins = new ArrayList<>();
        for (PluginVO pluginEntry : PluginCoreDAO.getInstance().getPluginVOs()) {
            if (pluginEntry.getPlugin() == null) {
                continue;
            }
            if (StringUtils.isEmpty(pluginEntry.getPlugin().getPreviewImageBase64())) {
                pluginEntry.getPlugin().setPreviewImageBase64("");
            }
            allPlugins.add(pluginEntry.getPlugin());
        }
        return allPlugins;
    }

    static boolean allRunning(PluginScanRunnable pluginScanRunnable) {
        if (pluginScanRunnable == null) {
            return false;
        }
        return pluginScanRunnable.getAllRunnablePluginIds().stream().allMatch(PluginSessions::isRunningByPluginId);
    }

    public static boolean isRunningByPluginId(String pluginId) {
        return getLocalSessionByPluginId(pluginId) != null;
    }

    public static boolean isRunningByPluginShortName(String pluginShortName) {
        return getLocalSessionByPluginShortName(pluginShortName) != null;
    }

    public static IOSession getLocalSessionByPluginShortName(String pluginShortName) {
        return firstOpenLocalSession(session -> matchesPluginShortName(session, pluginShortName));
    }

    public static IOSession getOrStartLocalSessionByPluginShortName(String pluginShortName) {
        IOSession session = getLocalSessionByPluginShortName(pluginShortName);
        if (session != null) {
            return session;
        }
        PluginVO pluginVO = PluginCoreDAO.getInstance().getPluginVOByShortName(pluginShortName);
        if (pluginVO == null || pluginVO.getPlugin() == null) {
            return null;
        }
        if (!PluginRuntimeStates.ensureStarted(pluginVO.getPlugin())) {
            return null;
        }
        return getLocalSessionByPluginShortName(pluginShortName);
    }

    public static IOSession getLocalSessionByPluginId(String pluginId) {
        return firstOpenLocalSession(session -> matchesPluginId(session, pluginId));
    }

    public static List<IOSession> getLocalSessionsByPluginId(String pluginId) {
        return openLocalSessions(session -> matchesPluginId(session, pluginId));
    }

    public static List<IOSession> closeLocalSessionsByPluginId(String pluginId) {
        return closeLocalSessions(session -> matchesPluginId(session, pluginId));
    }

    public static List<IOSession> closeLocalSessionsByPluginShortName(String pluginShortName) {
        return closeLocalSessions(session -> matchesPluginShortName(session, pluginShortName));
    }

    private static boolean hasOpenSessionForRuntimeInstance(String pluginId, String runtimeInstanceId) {
        for (IOSession session : getLocalSessionsByPluginId(pluginId)) {
            if (session.getPlugin() != null && Objects.equals(runtimeInstanceId, runtimeInstanceId(session))) {
                return true;
            }
        }
        return false;
    }

    private static void registerLocalSession(IOSession session) {
        if (session == null || session.getPlugin() == null || StringUtils.isEmpty(session.getPlugin().getId())) {
            return;
        }
        sessionId(session);
        if (!LOCAL_SESSIONS.contains(session)) {
            LOCAL_SESSIONS.add(session);
        }
    }

    private static void unregisterLocalSession(IOSession session) {
        if (session != null) {
            LOCAL_SESSIONS.remove(session);
        }
    }

    public static List<IOSession> getAllLocalSessions() {
        return new ArrayList<>(LOCAL_SESSIONS);
    }

    private static IOSession firstOpenLocalSession(Predicate<IOSession> matcher) {
        for (IOSession session : localSessions(matcher)) {
            if (isSessionOpen(session)) {
                return session;
            }
            unregisterPluginSession(session);
        }
        return null;
    }

    private static List<IOSession> openLocalSessions(Predicate<IOSession> matcher) {
        List<IOSession> sessions = new ArrayList<>();
        for (IOSession session : localSessions(matcher)) {
            if (isSessionOpen(session)) {
                sessions.add(session);
                continue;
            }
            unregisterPluginSession(session);
        }
        return sessions;
    }

    private static List<IOSession> closeLocalSessions(Predicate<IOSession> matcher) {
        List<IOSession> sessions = localSessions(matcher);
        for (IOSession session : sessions) {
            closeLocalSession(session);
        }
        return sessions;
    }

    private static List<IOSession> localSessions(Predicate<IOSession> matcher) {
        List<IOSession> sessions = new ArrayList<>();
        for (IOSession session : LOCAL_SESSIONS) {
            if (matcher.test(session)) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private static void closeLocalSession(IOSession session) {
        if (session == null) {
            return;
        }
        try {
            if (session.getSystemAttr().get("_channel") instanceof Channel) {
                session.close();
            }
        } finally {
            unregisterPluginSession(session);
        }
    }

    private static boolean matchesPluginId(IOSession session, String pluginId) {
        return session != null && session.getPlugin() != null && Objects.equals(pluginId, session.getPlugin().getId());
    }

    private static boolean matchesPluginShortName(IOSession session, String pluginShortName) {
        return session != null && session.getPlugin() != null
                && Objects.equals(pluginShortName, session.getPlugin().getShortName());
    }

    public static String sessionId(IOSession session) {
        Object existing = session.getSystemAttr().get(SESSION_ID_ATTR);
        if (existing != null && !existing.toString().trim().isEmpty()) {
            return existing.toString();
        }
        String sessionId = PluginRuntimeStates.newRuntimeInstanceId();
        session.getSystemAttr().put(SESSION_ID_ATTR, sessionId);
        return sessionId;
    }

    private static void attachProcessInfo(IOSession session, PluginScanRunnable pluginScanRunnable) {
        if (pluginScanRunnable == null || session == null || session.getPlugin() == null) {
            return;
        }
        String pluginId = session.getPlugin().getId();
        Long processId = pluginScanRunnable.processIdByPluginId(pluginId);
        if (processId == null) {
            return;
        }
        session.getSystemAttr().put(PROCESS_ID_ATTR, processId);
        pluginScanRunnable.runtimeInstanceIdByPluginId(pluginId)
                .ifPresent(runtimeInstanceId -> session.getSystemAttr().put(SESSION_ID_ATTR, runtimeInstanceId));
    }

    public static Long processId(IOSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getSystemAttr().get(PROCESS_ID_ATTR);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static String runtimeInstanceId(IOSession session) {
        return sessionId(session);
    }

    private static boolean isSessionOpen(IOSession session) {
        Object channel = session.getSystemAttr().get("_channel");
        return channel instanceof Channel && ((Channel) channel).isOpen();
    }
}
