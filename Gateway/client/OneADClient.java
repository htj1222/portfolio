package com.castis.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.ConfigAgent;
import com.castis.dto.constant.ConstantValue;
import com.castis.dto.request.AdRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OneADClient {
	static final Logger Logger = LogManager.getLogger(OneADClient.class);

	private String oneAdIp;
	private int oneAdPort;
	private Bootstrap httpBootStrap;
	private Channel httpChannel;
	private OneAdClientHandler oneAdHandler;
	private AdRequest adRequest;

	//http로 요청온 vod translate와 연결된 channel
	private Channel inboundChannel;

	private long sendSuccessTime;
	
	public OneADClient(String ip, int port, Channel inboundChannel, AdRequest adRequest) {
		this.oneAdIp = ip;
		this.oneAdPort = port;
		this.inboundChannel = inboundChannel;
		this.adRequest = adRequest;
		setBootstrap();
		makeChannelAndSendRequest();
	}

	private void setBootstrap() {
		//setting bootstrap
		Bootstrap b = new Bootstrap();
		if (inboundChannel != null) {
			Logger.debug("onead using inbound channel, is active? {}", inboundChannel.isActive());
			oneAdHandler = new OneAdClientHandler(this);
			httpBootStrap = configureHTTPBootstrap(b, inboundChannel.eventLoop());
		}
		httpBootStrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ConfigAgent.getInstance().getConfigDto().getOneAdConnectTimeoutMilliSec());
	}
	
	public void makeChannelAndSendRequest() {
		//connect and send request
		ChannelFuture f = httpBootStrap.connect(oneAdIp, oneAdPort);
		httpChannel = f.channel();
		f.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					sendAdRequest();
				} else {
					Logger.error("[requestid={}] onead request fail. [connect fail] oneadip({})", adRequest.getRequestId(), oneAdIp);
					sendFailResponse();
				}
			}
		});
	}
	
	public void sendFailResponse() {
		oneAdHandler.sendConnectFailResponse();
	}
	
	public void sendAdRequest() {
		if (httpChannel.isActive()) {
			Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
			String requestStr =  gson.toJson(adRequest);
			FullHttpRequest request = makeRequest(requestStr);
			
			httpChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					if (future.isSuccess()) {
						sendSuccessTime = System.currentTimeMillis();
						Logger.info("[requestid={}] onead request success. {} oneadip({})", adRequest.getRequestId(), adRequest.toString().toLowerCase(), oneAdIp);
					} else {
						Logger.error("[requestid={}] onead request fail. [send fail] oneadip({})", adRequest.getRequestId(), oneAdIp);
						sendFailResponse();
						future.channel().close();
					}
				}
			});
		} else {
			Logger.error("[requestid={}] onead request fail. [send channel is inactive] oneadip({})", adRequest.getRequestId(), oneAdIp);
			sendFailResponse();
		}
	}

	public Bootstrap configureHTTPBootstrap(Bootstrap b, EventLoopGroup g) {
		String osName = System.getProperty("os.name");
		b.group(g);
		if (osName.equals("Linux") == false) {
			b.channel(NioSocketChannel.class);
		} else {
			b.channel(EpollSocketChannel.class);			
		}
		b.remoteAddress(oneAdIp, oneAdPort).handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) {
				try {
					ChannelPipeline pipeline = ch.pipeline();
					pipeline.addLast("encoder", new HttpRequestEncoder());
					pipeline.addLast("decode", new HttpResponseDecoder());
					pipeline.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
					pipeline.addLast("readTimout", new ReadTimeoutHandler(ConfigAgent.getInstance().getConfigDto().getOneAdReadTimeoutMilliSec(), TimeUnit.MILLISECONDS));
					pipeline.addLast("handler", oneAdHandler);
				} catch (Exception e) {
					Logger.error("[requestid={}] onead request fail. [create client init failed {}] oneadip({})", adRequest.getRequestId(), e.toString().toLowerCase(), oneAdIp);
				}
			}
		});
		
		return b;
	}

	public Channel getHTTPChannel() {
		return httpChannel;
	}

	public Channel getInboundChannel() {
		return inboundChannel;
	}

	public AdRequest getAdRequest() {
		return adRequest;
	}

	public FullHttpRequest makeRequest(String requestStr) {
		FullHttpRequest httpPostRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, ConstantValue.ADREQUEST_URI);
		
		if (requestStr.isEmpty() == false) {
			ByteBuf buffer = Unpooled.copiedBuffer(requestStr, CharsetUtil.UTF_8);
			httpPostRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			httpPostRequest.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
			httpPostRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
			httpPostRequest.content().clear().writeBytes(buffer);
		}		

		return httpPostRequest;
	}
	
	public long getSendSuccessTime() {
		return sendSuccessTime;
	}
}
