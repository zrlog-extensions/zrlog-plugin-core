package com.zrlog.plugincore.server.impl;

import com.google.gson.Gson;
import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.api.IActionHandler;
import com.zrlog.plugin.common.model.Comment;
import com.zrlog.plugin.common.model.CreateArticleRequest;
import com.zrlog.plugin.common.model.PublicInfo;
import com.zrlog.plugin.common.model.TemplatePath;
import com.zrlog.plugin.data.codec.BaseHttpRequestInfo;
import com.zrlog.plugin.data.codec.HttpResponseInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugincore.server.Application;
import com.zrlog.plugincore.server.config.PluginConfig;
import com.zrlog.plugincore.server.dao.ArticleDAO;
import com.zrlog.plugincore.server.dao.CommentDAO;
import com.zrlog.plugincore.server.dao.TypeDAO;
import com.zrlog.plugincore.server.dao.WebSiteDAO;
import com.zrlog.plugincore.server.type.PluginStatus;
import com.zrlog.plugincore.server.util.HttpUtils;
import com.zrlog.plugincore.server.util.PluginUtil;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerActionHandler implements IActionHandler {

    private static final Logger LOGGER = LoggerUtil.getLogger(ServerActionHandler.class);

    @Override
    public void service(final IOSession session, final MsgPacket msgPacket) {
        handleMassagePackage(session, msgPacket);
    }

    private void handleMassagePackage(final IOSession session, final MsgPacket msgPacket) {
        if (msgPacket.getStatus() == MsgPacketStatus.SEND_REQUEST) {
            Map<String, Object> map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
            String name = map.get("name").toString();
            final IOSession serviceSession = PluginConfig.getInstance().getIOSessionByService(name);
            if (serviceSession != null) {
                // 消息中转
                serviceSession.requestService(name, map, responseMsgPacket -> {
                    responseMsgPacket.setMsgId(msgPacket.getMsgId());
                    session.sendMsg(responseMsgPacket);
                });
            } else {
                // not found service response error
                Map<String, Object> response = new HashMap<>();
                response.put("status", 500);
                session.sendJsonMsg(response, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
            }
        }
    }

    private static void doRefreshCache(int retryCount) {
        if (RunConstants.runType != RunType.BLOG) {
            return;
        }
        if (Application.BLOG_PORT <= 0) {
            return;
        }
        try {
            Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put("X-Plugin-Token", Application.BLOG_PLUGIN_TOKEN);
            byte[] bytes = HttpUtils.sendGetRequest("http://localhost:" + Application.BLOG_PORT + "/api/admin/refreshCache", requestHeaders);
            if (EnvKit.isDevMode()) {
                LOGGER.info("refresh cache success " + new String(bytes));
            }
        } catch (Exception e) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                //ignore
            }
            if (retryCount > 0) {
                retryCount -= 1;
                //重试更新，避免主程序还未启动
                doRefreshCache(retryCount);
                return;
            }
            LOGGER.warning("Refresh cache failed,  " + e.getMessage());
        }
    }

    @Override
    public void initConnect(IOSession session, MsgPacket msgPacket) {
        Plugin plugin = new Gson().fromJson(msgPacket.getDataStr(), Plugin.class);
        session.setPlugin(plugin);
        Map<String, String> map = new HashMap<>();
        map.put("runType", RunConstants.runType.toString());
        session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        PluginUtil.registerPlugin(PluginStatus.START, session);
        doRefreshCache(20);
    }

    @Override
    public void getFile(IOSession session, MsgPacket msgPacket) {

    }

    private String toWebSiteName(IOSession session, String key) {
        return session.getPlugin().getShortName() + "_" + key;
    }

    @Override
    public void loadWebSite(IOSession session, MsgPacket msgPacket) {
        Map map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
        String[] rawKeys = ((String) map.get("key")).split(",");
        try {
            Map<String, Object> webSiteByNameIn = new WebSiteDAO().getWebSiteByNameIn(Arrays.asList(Arrays.stream(rawKeys).map(e -> {
                return toWebSiteName(session, e);
            }).toArray(String[]::new)));
            Map<String, Object> resultMap = new LinkedHashMap<>();
            for (String rawKey : rawKeys) {
                resultMap.put(rawKey, webSiteByNameIn.get(toWebSiteName(session, rawKey)));
            }
            session.sendJsonMsg(resultMap, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public void setWebSite(IOSession session, MsgPacket msgPacket) {
        Map<String, Object> map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);

        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Map<String, Object> result = new HashMap<>();
            try {
                result.put("result", new WebSiteDAO().saveOrUpdate(toWebSiteName(session, entry.getKey()), entry.getValue()));
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
            resultMap.put(entry.getKey(), result);
        }
        if (map.get("syncTemplate") != null) {
            if (ResultValueConvertUtils.toBoolean(map.get("syncTemplate"))) {
                String accessHost = (String) map.get("host");
                String accessFolder = (String) map.get("folder");
                if (accessHost != null && accessFolder != null) {
                    accessHost = accessFolder + "/" + accessFolder;
                }
                if (accessHost != null) {
                    try {
                        new WebSiteDAO().saveOrUpdate("staticResourceHost", accessHost);
                    } catch (SQLException e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    }
                }
            } else {
                try {
                    new WebSiteDAO().saveOrUpdate("staticResourceHost", "");
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "", e);
                }
            }
        }
        session.sendJsonMsg(resultMap, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        try {
            doRefreshCache(20);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void httpMethod(final IOSession session, final MsgPacket msgPacket) {
        //handleMassagePackage(session, msgPacket);
        try {
            BaseHttpRequestInfo httpRequestInfo = new Gson().fromJson(msgPacket.getDataStr(), BaseHttpRequestInfo.class);
            HttpResponseInfo httpResponseInfo = HttpUtils.doRequest(httpRequestInfo);
            session.sendJsonMsg(httpResponseInfo, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (Exception e) {
            HttpResponseInfo httpResponseInfo = new HttpResponseInfo();
            httpResponseInfo.setStatusCode(500);
            httpResponseInfo.setHeader(new HashMap<>());
            httpResponseInfo.setResponseBody(LoggerUtil.recordStackTraceMsg(e).getBytes(StandardCharsets.UTF_8));
            session.sendJsonMsg(httpResponseInfo, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        }
    }

    @Override
    public void deleteComment(IOSession session, MsgPacket msgPacket) {
        Comment comment = new Gson().fromJson(msgPacket.getDataStr(), Comment.class);
        Map<String, Boolean> map = new HashMap<>();
        if (comment.getPostId() != null) {
            try {
                boolean result = new CommentDAO().set("postId", comment.getPostId()).delete();
                map.put("result", result);
                session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
            } catch (SQLException e) {
                map.put("result", false);
                LOGGER.log(Level.SEVERE, "delete comment error", e);
                session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
            }
        }
    }

    @Override
    public void addComment(IOSession session, MsgPacket msgPacket) {
        Comment comment = new Gson().fromJson(msgPacket.getDataStr(), Comment.class);
        Map<String, Boolean> map = new HashMap<>();
        try {
            boolean result = new CommentDAO().set("userHome", comment.getHome()).set("userMail", comment.getMail()).set("userIp", comment.getIp()).set("userName", comment.getName()).set("logId", comment.getLogId()).set("postId", comment.getPostId()).set("userComment", comment.getContent()).set("commTime", comment.getCreatedTime()).set("td", new Date()).set("header", comment.getHeadPortrait()).set("hide", 1).save();

            map.put("result", result);
            session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (SQLException e) {
            map.put("result", false);
            LOGGER.log(Level.SEVERE, "save comment error", e);
            session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
        }
    }

    @Override
    public void plugin(IOSession session, MsgPacket msgPacket) {

    }

    @Override
    public void getDbProperties(IOSession session, MsgPacket msgPacket) {
        Map<String, Object> map = new HashMap<>();
        map.put("dbProperties", PluginConfig.getInstance().getDbPropertiesFile().toString());
        session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
    }

    @Override
    public void attachment(IOSession session, MsgPacket msgPacket) {

    }


    @Override
    public void loadPublicInfo(IOSession session, MsgPacket msgPacket) {
        String[] keys = "title,second_title,host,admin_darkMode,admin_color_primary".split(",");
        try {
            Map<String, Object> response = new WebSiteDAO().getWebSiteByNameIn(Arrays.asList(keys));
            // convert to publicInfo
            PublicInfo publicInfo = new PublicInfo();
            publicInfo.setHomeUrl("http://" + response.get("host"));
            publicInfo.setApiHomeUrl("http://127.0.0.1:" + (Application.BLOG_PORT > 0 ? Application.BLOG_PORT : 6058));
            publicInfo.setTitle((String) response.get("title"));
            publicInfo.setSecondTitle((String) response.get("second_title"));
            publicInfo.setAdminColorPrimary((String) response.get("admin_color_primary"));
            publicInfo.setDarkMode(ResultValueConvertUtils.toBoolean(response.get("admin_darkMode")));
            session.sendJsonMsg(publicInfo, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public void getCurrentTemplate(IOSession session, MsgPacket msgPacket) {
        try {
            String templatePath = (String) new WebSiteDAO().set("name", "template").queryFirst("value");
            TemplatePath template = new TemplatePath();
            template.setValue(templatePath);
            session.sendJsonMsg(template, ActionType.CURRENT_TEMPLATE.name(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public void getBlogRuntimePath(IOSession session, MsgPacket msgPacket) {
        session.sendJsonMsg(PluginConfig.getInstance().getBlogRunTime(), ActionType.BLOG_RUN_TIME.name(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
    }

    public String getPlainSearchTxt(String content) {
        return Jsoup.parse(content).body().text();
    }

    @Override
    public void createArticle(IOSession session, MsgPacket msgPacket) {
        CreateArticleRequest createArticleRequest = new Gson().fromJson(msgPacket.getDataStr(), CreateArticleRequest.class);
        Integer typeId = 0;
        if (createArticleRequest.getTypeId() > 0) {
            typeId = createArticleRequest.getTypeId();
        } else {
            try {
                typeId = (Integer) new TypeDAO().findByName(createArticleRequest.getType());
                if (typeId == null) {
                    new TypeDAO().set("typeName", createArticleRequest.getType()).set("alias", createArticleRequest.getType()).save();
                    //query again;
                    typeId = (Integer) new TypeDAO().findByName(createArticleRequest.getType());

                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        String alias = createArticleRequest.getAlias();

        if (alias == null) {
            try {
                alias = new ArticleDAO().queryFirstObj("select max(logId) from log") + "";
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        Map<String, Boolean> map = new HashMap<>();
        try {
            Integer logId = (Integer) new ArticleDAO().queryFirstObj("select logId from log where alias = ?", alias);
            DAO articleDAO = new ArticleDAO().set("releaseTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(createArticleRequest.getReleaseDate())).set("last_update_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(createArticleRequest.getReleaseDate())).set("content", createArticleRequest.getContent()).set("title", createArticleRequest.getTitle()).set("markdown", createArticleRequest.getMarkdown()).set("digest", createArticleRequest.getDigest()).set("typeId", typeId).set("private", createArticleRequest.is_private()).set("rubbish", createArticleRequest.isRubbish()).set("alias", alias).set("plain_content", getPlainSearchTxt(createArticleRequest.getContent())).set("thumbnail", createArticleRequest.getThumbnail()).set("canComment", createArticleRequest.isCanComment()).set("recommended", createArticleRequest.isRecommended()).set("keywords", createArticleRequest.getKeywords()).set("editor_type", createArticleRequest.getEditorType()).set("userId", createArticleRequest.getUserId());
            if (logId == null) {
                try {
                    boolean result = articleDAO.save();
                    doRefreshCache(20);
                    map.put("result", result);
                    session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
                } catch (Exception e) {
                    map.put("result", false);
                    LOGGER.log(Level.SEVERE, "save comment error", e);
                    session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
                }
            } else {
                try {
                    Map<String, Object> cond = new HashMap<>();
                    cond.put("logId", logId);
                    boolean result = articleDAO.update(cond);
                    doRefreshCache(20);
                    map.put("result", result);
                    session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
                } catch (Exception e) {
                    map.put("result", false);
                    LOGGER.log(Level.SEVERE, "save comment error", e);
                    session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void refreshCache(IOSession session, MsgPacket msgPacket) {
        doRefreshCache(20);
    }

    @Override
    public void articleVisitViewCountAddOne(IOSession session, MsgPacket msgPacket) {
        Map info = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
        String alias = info.get("alias").toString();
        Map<String, Object> map = new HashMap<>();

        try {
            new DAO().execute("update log set click = click + 1  where logId=? or alias=?", alias, alias);
            map.put("result", true);
            session.sendJsonMsg(map, msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
        } catch (SQLException e) {
            map.put("result", false);
            map.put("message", LoggerUtil.recordStackTraceMsg(e));
            session.sendJsonMsg(new HashMap<>(), msgPacket.getMethodStr(), msgPacket.getMsgId(), MsgPacketStatus.RESPONSE_ERROR);
        }
    }
}
