package com.castis.adgateway;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;

import com.castis.agent.ConfigAgent;
import com.castis.agent.DnsResolveAgent;
import com.castis.agent.XmlMapperAgent;
import com.castis.dto.ConfigDto;
import com.castis.runnable.CheckNotificationTimeoutRunnable;
import com.castis.util.CiLoggerAsync;

public class ADGateway {
	private final static String ADGatewayName = "adgateway";
	private final static String ADGatewayVersion = "1.0.0.RC2";

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equalsIgnoreCase("-version") == true) {
				System.out.println(ADGatewayName + "_64 Version " + ADGatewayVersion);
				return;
			} else if (arg.equalsIgnoreCase("-v") == true) {
				System.out.println(ADGatewayName + "_64 Version " + ADGatewayVersion.substring(0, ADGatewayVersion.lastIndexOf('.')));
				return;
			}
		}

		// set config
		ConfigDto configDto = setConfig();
		if (configDto == null) {
			System.out.println("there is some error in configuration file (ADGateway.cfg). exit now.");
			System.exit(0);
		}

		System.out.println("Print config : " + configDto.toString());

		// set logger
		setLogger(configDto);

		if (configDto.isOneAdIpIsDns() == true) {
			EventLoopGroup transcasterLoopGroup = new NioEventLoopGroup(1);
			DnsResolveAgent.getInstance().setEventLoopGroup(transcasterLoopGroup);
			DnsResolveAgent.getInstance().getIp(configDto.getOneAdIp());
		}

		final Logger Logger = LogManager.getLogger(ADGateway.class);

		// SIG detect thread
		final int currentPid = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Logger.fatal("{} killed by user. pid:({})", ADGatewayName, currentPid);
				System.out.println(ADGatewayName + " killed by user. PID: " + currentPid);
			}
		});

		Logger.info("adgateway configuration load Successed!");

		// Service monitor(for duplication)
		EventLoopGroup udpWorkerGroup = new NioEventLoopGroup();
		try {
			// UDP socket
			Bootstrap udpBootstrap = new Bootstrap();
			udpBootstrap.group(udpWorkerGroup).channel(NioDatagramChannel.class).handler(new ServiceMonitorHandler());
			udpBootstrap.bind(configDto.getHeartbeatPort()).sync();
			Logger.info(ADGatewayName + " service monitor started in port:" + configDto.getHeartbeatPort());
		} catch (Exception e1) {
			Logger.error("service moitor heartbeat port connection exception: {}", e1);
			System.exit(0);
		}

		// notification timeout checker
		EventExecutorGroup checkNotificationEventGroup = new DefaultEventExecutorGroup(1);
		CheckNotificationTimeoutRunnable checkNotificationTimeoutRunnable = new CheckNotificationTimeoutRunnable(configDto.getNotificationTimeoutMilliSec());
		checkNotificationEventGroup.scheduleAtFixedRate(checkNotificationTimeoutRunnable, 0, 1, TimeUnit.SECONDS);

		// get config
		String serverIp = configDto.getServerIp();
		int httpVodTranslatePort = configDto.getServerVodTranslatePort();
		int httpCmsPort = configDto.getServerCmsPort();

		// pre create static class
		XmlMapperAgent.getInstance().getXmlMapper();

		// HTTP Listener
		String osName = System.getProperty("os.name");
		int threadPoolSize = configDto.getThreadPoolSize();

		EventLoopGroup httpBossGroupForVod;
		EventLoopGroup httpWorkerGroupForVod;
		ServerBootstrap httpBootstrapForVod = new ServerBootstrap();

		EventLoopGroup httpBossGroupForCms;
		EventLoopGroup httpWorkerGroupForCms;
		ServerBootstrap httpBootstrapForCms = new ServerBootstrap();

		if (osName.equals("Linux") == false) {
			httpBossGroupForVod = new NioEventLoopGroup(1);
			httpWorkerGroupForVod = new NioEventLoopGroup(threadPoolSize);
			httpBossGroupForCms = new NioEventLoopGroup(1);
			httpWorkerGroupForCms = new NioEventLoopGroup(threadPoolSize);
		} else {
			httpBossGroupForVod = new EpollEventLoopGroup(1);
			httpWorkerGroupForVod = new EpollEventLoopGroup(threadPoolSize);
			httpBossGroupForCms = new EpollEventLoopGroup(1);
			httpWorkerGroupForCms = new EpollEventLoopGroup(threadPoolSize);
		}

		httpBootstrapForVod.group(httpBossGroupForVod, httpWorkerGroupForVod).childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				pipeline.addLast(new HttpServerCodec());
				pipeline.addLast("decoder", new HttpRequestDecoder());
				pipeline.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
				pipeline.addLast(new HTTPHandler());
			}
		});

		httpBootstrapForCms.group(httpBossGroupForCms, httpWorkerGroupForCms).childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				pipeline.addLast(new HttpServerCodec());
				pipeline.addLast("decoder", new HttpRequestDecoder());
				pipeline.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
				pipeline.addLast(new HTTPHandler());
			}
		});

		if (osName.equals("Linux") == false) {
			httpBootstrapForVod.channel(NioServerSocketChannel.class);
			httpBootstrapForCms.channel(NioServerSocketChannel.class);
		} else {
			httpBootstrapForVod.channel(EpollServerSocketChannel.class);
			httpBootstrapForCms.channel(EpollServerSocketChannel.class);
			httpBootstrapForVod.option(EpollChannelOption.SO_REUSEPORT, true);
			httpBootstrapForCms.option(EpollChannelOption.SO_REUSEPORT, true);
		}

		httpBootstrapForVod.bind(new InetSocketAddress(serverIp, httpVodTranslatePort));
		httpBootstrapForCms.bind(new InetSocketAddress(serverIp, httpCmsPort));

		Logger.info(ADGatewayName + " " + ADGatewayVersion + " started in http port({}:{}) for vod translate", serverIp, httpVodTranslatePort);
		Logger.info(ADGatewayName + " " + ADGatewayVersion + " started in http port({}:{}) for cms", serverIp, httpCmsPort);
	}

	public static ConfigDto setConfig() {
		ConfigAgent configAgent = ConfigAgent.getInstance();
		return configAgent.getConfigDto();
	}

	public static void setLogger(ConfigDto configDto) {
		String path = ADGateway.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String currentFullPath = "";
		try {
			currentFullPath = URLDecoder.decode(path, "UTF-8");
		} catch (Exception e) {
			currentFullPath = path;
		}

		String currentPath = currentFullPath.substring(0, path.lastIndexOf("/") + 1);
		String currentRunningPath = currentPath + "/log4j2.xml";

		String logDir = configDto.getLogDirSrc();
		String logFile = configDto.getLogFileName();
		String logLevel = configDto.getLogLevel();
		int logMaxSize = configDto.getLogMaxSizeMB();
		new CiLoggerAsync(logDir, logFile, currentRunningPath, logLevel, logMaxSize);
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
		File file = new File(currentRunningPath);

		// this will force a reconfiguration
		context.setConfigLocation(file.toURI());
		StatusLogger.getLogger().setLevel(Level.DEBUG);
	}

	public static String serverName() {
		return ADGatewayName;
	}

	public static String serverVersion() {
		return ADGatewayVersion;
	}
}
