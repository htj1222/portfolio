package com.castis.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.DibaSubagentAgent;
import com.castis.dao.elasticsearch.StatusDaoImpl;
import com.castis.dto.ChangeHistory;
import com.castis.dto.ChannelStatus;
import com.castis.dto.CurrentChannel;
import com.castis.dto.DailyServerStatus;
import com.castis.dto.DumpResult;
import com.castis.dto.MostViewdChannelList;
import com.castis.dto.MulticastStatus;
import com.castis.dto.PhysicalDrive;
import com.castis.dto.PowerSupply;
import com.castis.dto.Region;
import com.castis.dto.SdvProgramLog;
import com.castis.dto.ServerStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DibaSubagentClient {
	static final Logger Logger = LogManager.getLogger(DibaSubagentClient.class);
	Gson gson = new GsonBuilder().disableHtmlEscaping().create();

	private Region regionInfo;
	private String dibaSubagentIp;
	private int dibaSubagentHTTPPort;
	private Bootstrap dibaSubagentHTTPBootStrap;
	private Channel dibaSubagentHTTPChannel;
	private DibaSubagentHTTPHandler dibaSubagentHandler;
	
	static int CONNECT_TIME_OUT_MILLIS = 3000;

	public DibaSubagentClient(Region region) {
		this.regionInfo = region;
		this.dibaSubagentIp = region.getDibaSubagentIp();
		this.dibaSubagentHTTPPort = region.getDibaSubagentPort();
		makeHTTPConnect();
	}

	private void makeHTTPConnect() {
		Channel connectedChannel = null;
		dibaSubagentHandler = new DibaSubagentHTTPHandler(dibaSubagentIp, dibaSubagentHTTPPort);
		Bootstrap b = new Bootstrap();
		dibaSubagentHTTPBootStrap = configureHTTPBootstrap(b);
		dibaSubagentHTTPBootStrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIME_OUT_MILLIS);

		try {
			connectedChannel = dibaSubagentHTTPBootStrap.connect(dibaSubagentIp, dibaSubagentHTTPPort).sync().channel();
		} catch (Exception e) {
			Logger.error("DibaSubagent HTTP[{}:{}] Exception:", dibaSubagentIp, dibaSubagentHTTPPort, e.getMessage());
		}

		if (connectedChannel == null) {
			Logger.error("DibaSubagent HTTP[{}:{}] can't connect.", dibaSubagentIp, dibaSubagentHTTPPort);
		} else {
			Logger.debug("DibaSubagent HTTP[{}:{}] connect Successed", dibaSubagentIp, dibaSubagentHTTPPort);
			dibaSubagentHTTPChannel = connectedChannel;
		}
	}

	private Bootstrap configureHTTPBootstrap(Bootstrap b) {
		try {
			Bootstrap newBootstrap = configureHTTPBootstrap(b, DibaSubagentAgent.getInstance().getDibaSubagentEventLoopGroup());
			return newBootstrap;
		} catch (Exception e) {
			Logger.error("Exception:", e);
			e.printStackTrace();
			return null;
		}
	}

	public Bootstrap configureHTTPBootstrap(Bootstrap b, EventLoopGroup g) {
		b.group(g).channel(NioSocketChannel.class).remoteAddress(dibaSubagentIp, dibaSubagentHTTPPort).handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) {
				try {
					ChannelPipeline pipeline = ch.pipeline();
					pipeline.addLast("decoder", new HttpResponseDecoder());
					pipeline.addLast("encoder", new HttpRequestEncoder());
					pipeline.addLast("codec", new HttpServerCodec());
					pipeline.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
					pipeline.addLast("http-chunked", new ChunkedWriteHandler());
					pipeline.addLast("handler", dibaSubagentHandler);
				} catch (Exception e) {
					Logger.error("create HTTP DibaSubagent client init failed:", e);
				}
			}
		});
		return b;
	}
	
	public void closeChannel() {
		try {
			dibaSubagentHTTPChannel.closeFuture().sync();
		} catch (InterruptedException e) {
			Logger.error("dibaSubagent close channel fail ip({}:{}) e({}) ", dibaSubagentIp, dibaSubagentHTTPPort, e);
		}
	}
	
	public Channel getHTTPChannel() {
		return dibaSubagentHTTPChannel;
	}

	public ServerStatus getStatus() {
		String statusUri = "/status";
		HttpRequest statusGetRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, statusUri);

		dibaSubagentHTTPChannel.writeAndFlush(statusGetRequest);
		Logger.info("dibaSubagent request status ip : " + dibaSubagentIp + ":" + dibaSubagentHTTPPort);

		ServerStatus status = dibaSubagentHandler.getServerStatus();
		closeChannel();

		return status;
	}
}
