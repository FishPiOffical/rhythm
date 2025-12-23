/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.http.BaseServer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.cache.DomainCache;
import org.b3log.symphony.cache.TagCache;
import org.b3log.symphony.event.ArticleAddAudioHandler;
import org.b3log.symphony.event.ArticleAddNotifier;
import org.b3log.symphony.event.ArticleBaiduSender;
import org.b3log.symphony.event.ArticleSearchAdder;
import org.b3log.symphony.event.ArticleSearchUpdater;
import org.b3log.symphony.event.ArticleUpdateAudioHandler;
import org.b3log.symphony.event.ArticleUpdateNotifier;
import org.b3log.symphony.event.ChangeRoleHandler;
import org.b3log.symphony.event.ChangeRoleUpdateHandler;
import org.b3log.symphony.event.CommentNotifier;
import org.b3log.symphony.event.CommentUpdateNotifier;
import org.b3log.symphony.event.PrivateChatSendHandler;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.Router;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CronMgmtService;
import org.b3log.symphony.service.InitMgmtService;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.NodeUtil;
import org.b3log.symphony.util.ReservedWords;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.Vocation;
import org.json.JSONObject;

/**
 * Server.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.1.4, May 21, 2020
 * @since 3.4.8
 */
public final class Server extends BaseServer {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    /**
     * Symphony version.
     */
    public static final String VERSION = "3.6.3";

    /**
     * Fishing Pi version.
     */
    public static final String FISHING_PI_VERSION = "1.0.0";

    /**
     * Startup time && static version
     */
    public static String STARTUP_STR = "";

    /**
     * Main.
     *
     * @param args the specified arguments
     */
    public static void main(final String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Stopwatchs.start("Booting");

        final Options options = new Options();
        final Option listenPortOpt = Option.builder("lp").longOpt("listen_port").argName("LISTEN_PORT").hasArg().desc("listen port, default is 8080").build();
        options.addOption(listenPortOpt);

        final Option unixDomainSocketPathOpt = Option.builder().longOpt("unix_domain_socket_path").argName("UNIX_DOMAIN_SOCKET_PATH").hasArg().desc("unix domain socket path").build();
        options.addOption(unixDomainSocketPathOpt);

        final Option serverSchemeOpt = Option.builder("ss").longOpt("server_scheme").argName("SERVER_SCHEME").hasArg().desc("browser visit protocol, default is http").build();
        options.addOption(serverSchemeOpt);

        final Option serverHostOpt = Option.builder("sh").longOpt("server_host").argName("SERVER_HOST").hasArg().desc("browser visit domain name, default is localhost").build();
        options.addOption(serverHostOpt);

        final Option serverPortOpt = Option.builder("sp").longOpt("server_port").argName("SERVER_PORT").hasArg().desc("browser visit port, default is 8080").build();
        options.addOption(serverPortOpt);

        final Option staticServerSchemeOpt = Option.builder("sss").longOpt("static_server_scheme").argName("STATIC_SERVER_SCHEME").hasArg().desc("browser visit static resource protocol, default is http").build();
        options.addOption(staticServerSchemeOpt);

        final Option staticServerHostOpt = Option.builder("ssh").longOpt("static_server_host").argName("STATIC_SERVER_HOST").hasArg().desc("browser visit static resource domain name, default is localhost").build();
        options.addOption(staticServerHostOpt);

        final Option staticServerPortOpt = Option.builder("ssp").longOpt("static_server_port").argName("STATIC_SERVER_PORT").hasArg().desc("browser visit static resource port, default is 8080").build();
        options.addOption(staticServerPortOpt);

        final Option runtimeModeOpt = Option.builder("rm").longOpt("runtime_mode").argName("RUNTIME_MODE").hasArg().desc("runtime mode (DEVELOPMENT/PRODUCTION), default is DEVELOPMENT").build();
        options.addOption(runtimeModeOpt);

        options.addOption("h", "help", false, "print help for the command");

        final HelpFormatter helpFormatter = new HelpFormatter();
        final CommandLineParser commandLineParser = new DefaultParser();
        CommandLine commandLine;

        final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        final String cmdSyntax = isWindows ? "java -cp \"lib/*;.\" org.b3log.symphony.Server"
                : "java -cp \"lib/*:.\" org.b3log.symphony.Server";
        final String header = "\nSym 是一款用 Java 实现的现代化社区（论坛/BBS/社交网络/博客）平台。\n\n";
        final String footer = "\n提需求或报告缺陷请到项目网站: https://github.com/88250/symphony\n\n";
        try {
            commandLine = commandLineParser.parse(options, args);
        } catch (final ParseException e) {
            helpFormatter.printHelp(cmdSyntax, header, options, footer, true);
            return;
        }

        if (commandLine.hasOption("h")) {
            helpFormatter.printHelp(cmdSyntax, header, options, footer, true);
            return;
        }

        try {
            Latkes.setScanPath("org.b3log.symphony");
            Latkes.init();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Latke init failed, please configure latke.props or run with args, visit https://ld246.com/article/1492881378588 for more details");

            System.exit(-1);
        }

        String serverScheme = commandLine.getOptionValue("server_scheme");
        if (null != serverScheme) {
            Latkes.setLatkeProperty("serverScheme", serverScheme);
        }
        String serverHost = commandLine.getOptionValue("server_host");
        if (null != serverHost) {
            Latkes.setLatkeProperty("serverHost", serverHost);
        }
        String serverPort = commandLine.getOptionValue("server_port");
        if (null != serverPort) {
            Latkes.setLatkeProperty("serverPort", serverPort);
        }
        String staticServerScheme = commandLine.getOptionValue("static_server_scheme");
        if (null != staticServerScheme) {
            Latkes.setLatkeProperty("staticServerScheme", staticServerScheme);
        }
        String staticServerHost = commandLine.getOptionValue("static_server_host");
        if (null != staticServerHost) {
            Latkes.setLatkeProperty("staticServerHost", staticServerHost);
        }
        String staticServerPort = commandLine.getOptionValue("static_server_port");
        if (null != staticServerPort) {
            Latkes.setLatkeProperty("staticServerPort", staticServerPort);
        }
        String runtimeMode = commandLine.getOptionValue("runtime_mode");
        if (null != runtimeMode) {
            Latkes.setRuntimeMode(Latkes.RuntimeMode.valueOf(runtimeMode));
        }

        final Latkes.RuntimeDatabase runtimeDatabase = Latkes.getRuntimeDatabase();
        final String jdbcUsername = Latkes.getLocalProperty("jdbc.username");
        final String jdbcURL = Latkes.getLocalProperty("jdbc.URL");
        final boolean luteAvailable = Markdowns.LUTE_AVAILABLE;

        LOGGER.log(Level.INFO, "Rhythm is booting [ver=" + VERSION + ", os=" + Latkes.getOperatingSystemName() +
                ", isDocker=" + Latkes.isDocker() + ", luteAvailable=" + luteAvailable + ", pid=" + Latkes.currentPID() +
                ", runtimeDatabase=" + runtimeDatabase + ", runtimeMode=" + Latkes.getRuntimeMode() + ", jdbc.username=" +
                jdbcUsername + ", jdbc.URL=" + jdbcURL + "]");

        final BeanManager beanManager = BeanManager.getInstance();

        final InitMgmtService initMgmtService = beanManager.getReference(InitMgmtService.class);
        initMgmtService.initSym();

        // Register event listeners
        final EventManager eventManager = beanManager.getReference(EventManager.class);
        final ArticleAddNotifier articleAddNotifier = beanManager.getReference(ArticleAddNotifier.class);
        eventManager.registerListener(articleAddNotifier);
        final ArticleUpdateNotifier articleUpdateNotifier = beanManager.getReference(ArticleUpdateNotifier.class);
        eventManager.registerListener(articleUpdateNotifier);
        final ArticleBaiduSender articleBaiduSender = beanManager.getReference(ArticleBaiduSender.class);
        eventManager.registerListener(articleBaiduSender);
        final CommentNotifier commentNotifier = beanManager.getReference(CommentNotifier.class);
        eventManager.registerListener(commentNotifier);
        final CommentUpdateNotifier commentUpdateNotifier = beanManager.getReference(CommentUpdateNotifier.class);
        eventManager.registerListener(commentUpdateNotifier);
        final ArticleSearchAdder articleSearchAdder = beanManager.getReference(ArticleSearchAdder.class);
        eventManager.registerListener(articleSearchAdder);
        final ArticleSearchUpdater articleSearchUpdater = beanManager.getReference(ArticleSearchUpdater.class);
        eventManager.registerListener(articleSearchUpdater);
        final ArticleAddAudioHandler articleAddAudioHandler = beanManager.getReference(ArticleAddAudioHandler.class);
        eventManager.registerListener(articleAddAudioHandler);
        final ArticleUpdateAudioHandler articleUpdateAudioHandler = beanManager.getReference(ArticleUpdateAudioHandler.class);
        eventManager.registerListener(articleUpdateAudioHandler);
        final ChangeRoleHandler roleHandler = beanManager.getReference(ChangeRoleHandler.class);
        eventManager.registerListener(roleHandler);
        final ChangeRoleUpdateHandler roleUpdateHandler = beanManager.getReference(ChangeRoleUpdateHandler.class);
        eventManager.registerListener(roleUpdateHandler);
        final PrivateChatSendHandler privateChatSendHandler = beanManager.getReference(PrivateChatSendHandler.class);
        eventManager.registerListener(privateChatSendHandler);

        final TagCache tagCache = beanManager.getReference(TagCache.class);
        tagCache.loadTags();
        final DomainCache domainCache = beanManager.getReference(DomainCache.class);
        domainCache.loadDomains();
        final CronMgmtService cronMgmtService = beanManager.getReference(CronMgmtService.class);
        cronMgmtService.start();

        // 请求路由映射
        Router.requestMapping();

        Stopwatchs.end();
        LOGGER.log(Level.DEBUG, "Stopwatch: {}{}", Strings.LINE_SEPARATOR, Stopwatchs.getTimingStat());
        Stopwatchs.release();

        final Server server = new Server();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // 用户层 结算似乎不需要 暂时去掉
            UserChannel.settlement();
            // 框架
            cronMgmtService.stop();
            server.shutdown();
            Symphonys.EXECUTOR_SERVICE.shutdown();
            Latkes.shutdown();
            LogManager.shutdown();
        }));

        LOGGER.log(Level.INFO, "Everything is ready, Thank you for using Rhythm!");

        System.out.println(">>> Quick boot mode requirements is processing...");
        // 强制离线所有用户，防止在线 Flag 出现问题
        System.out.println(">>> Resetting users online status...");
        final UserRepository userRepository = beanManager.getReference(UserRepository.class);
        final Transaction transaction = userRepository.beginTransaction();
        try {
            final Query query = new Query();
            List<JSONObject> userList = userRepository.getList(query);
            boolean has = false;
            for (JSONObject user : userList) {
                String oId = user.optString(Keys.OBJECT_ID);
                Boolean userOnlineFlag = user.optBoolean(UserExt.USER_ONLINE_FLAG);
                if (userOnlineFlag) {
                    user.put(UserExt.USER_ONLINE_FLAG, false);
                    userRepository.update(oId, user);
                    System.out.print("「" + user.optString(User.USER_NAME) + "」 ");
                    has = true;
                }
            }
            transaction.commit();
            if (has) System.out.println();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Cannot offline all users forced", e);
        }
        ReservedWords.init();
        final ArticleQueryService articleQueryService = beanManager.getReference(ArticleQueryService.class);
        articleQueryService.refreshHotArticlesCache();

        Thread.startVirtualThread(() -> {
            if (!Vocation.refresh()) {
                Vocation.refresh();
            }
        });

        Thread.startVirtualThread(() -> {
            NodeUtil.init();
            NodeUtil.initOnline();
        });

        String startupTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String staticResourceVersion = Symphonys.get("staticResourceVersion");
        STARTUP_STR = "<li>Rhythm 启动时间：" + startupTime + "</li><li>Rhythm 编译版本：" + staticResourceVersion + "</li>";

        System.out.println(">>> Quick boot mode requirements is ready!");

        final String unixDomainSocketPath = commandLine.getOptionValue("unix_domain_socket_path");
        if (StringUtils.isNotBlank(unixDomainSocketPath)) {
            server.start(unixDomainSocketPath);
        } else {
            String portArg = commandLine.getOptionValue("listen_port");
            if (!Strings.isNumeric(portArg)) {
                portArg = "8080";
            }
            //PointtransferSyncTool.syncAllHistoryToRedc();
            server.start(Integer.parseInt(portArg));
        }
    }
}
