package com.castis.idasbackend;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpServerCodec;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;

import com.castis.agent.ConfigAgent;
import com.castis.agent.DibaSubagentAgent;
import com.castis.agent.ESAgent;
import com.castis.dto.ConfigDto;
import com.castis.runnable.ConfigHistoryGetterRunnable;
import com.castis.runnable.DailyIptvQualityGetterJob;
import com.castis.runnable.DailyStatusGetterJob;
import com.castis.runnable.RatingGetterJob;
import com.castis.runnable.StatusGetterRunnable;
import com.castis.util.CiLoggerAsync;

public class IDASBackEnd {
	private final static String iDASBackEndName = "iDASBackEnd";
	private final static String iDASBackEndVersion = "0.0.1.RC1";

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equalsIgnoreCase("-version") == true) {
				System.out.println(iDASBackEndName + "_64 Version " + iDASBackEndVersion);
				return;
			} else if (arg.equalsIgnoreCase("-v") == true) {
				System.out.println(iDASBackEndName + "_64 Version " + iDASBackEndVersion.substring(0, iDASBackEndVersion.lastIndexOf('.')));
				return;
			}
		}

		/** 중략 **/
		
		String serverIp = iDASConfigDto.getServerIp();
		int udpPort = iDASConfigDto.getServerUDPPort();
		int httpPort = iDASConfigDto.getServerHTTPPort();
		
		// HTTP Listener
		EventLoopGroup httpBossGroup = new NioEventLoopGroup();
		EventLoopGroup httpWorkerGroup = new NioEventLoopGroup();
		ServerBootstrap httpBootstrap = new ServerBootstrap();
		httpBootstrap.group(httpBossGroup, httpWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				pipeline.addLast(new HttpServerCodec());
				pipeline.addLast("decoder", new HttpRequestDecoder());
				pipeline.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
				pipeline.addLast(new HTTPHandler());
			}
		});
		httpBootstrap.bind(new InetSocketAddress(serverIp, httpPort));
		Logger.info(iDASBackEndName + " " + iDASBackEndVersion + " Started in HTTP port({}:{})", serverIp, httpPort);
		
		ScheduledThreadPoolExecutor statusGetterGroup = new ScheduledThreadPoolExecutor(1);
		statusGetterGroup.scheduleAtFixedRate(new StatusGetterRunnable(), 1, 10, TimeUnit.MINUTES);
			
		ScheduledThreadPoolExecutor dailyIptvQualityEventGroup = new ScheduledThreadPoolExecutor(1);
		dailyIptvQualityEventGroup.scheduleAtFixedRate(new DailyIptvQualityGetterJob(), 0, 1, TimeUnit.HOURS);

		/** 중략 **/
	}

	public static String serverName() {
		return iDASBackEndName;
	}

	public static String serverVersion() {
		return iDASBackEndVersion;
	}
}
