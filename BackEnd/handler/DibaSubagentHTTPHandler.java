package com.castis.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.ChannelVerificationAgent;
import com.castis.agent.ConfigAgent;
import com.castis.dto.ChangeHistory;
import com.castis.dto.ChannelStatus;
import com.castis.dto.CurrentChannel;
import com.castis.dto.DailyServerStatus;
import com.castis.dto.DumpResult;
import com.castis.dto.MostViewdChannelList;
import com.castis.dto.MulticastStatus;
import com.castis.dto.PhysicalDrive;
import com.castis.dto.PowerSupply;
import com.castis.dto.SdvProgramLog;
import com.castis.dto.ServerStatus;
import com.castis.util.DateDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Sharable
public class DibaSubagentHTTPHandler extends SimpleChannelInboundHandler<HttpObject> {
	static final Logger Logger = LogManager.getLogger(DibaSubagentHTTPHandler.class);

	private String currentIpAddress;
	private int currentPort;

	private BlockingQueue<FullHttpResponse> receivedMessageBlockingQueue = new LinkedBlockingQueue<FullHttpResponse>();
	private Gson gson;

	private int receiveTimeoutMilliSec;

	public DibaSubagentHTTPHandler(String DibaSubagentIpAddress, int DibaSubagentPort) {
		currentIpAddress = DibaSubagentIpAddress;
		currentPort = DibaSubagentPort;
		receivedMessageBlockingQueue.clear();
		receiveTimeoutMilliSec = 3000;
		gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateDeserializer()).disableHtmlEscaping().create();
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) {
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent e = (IdleStateEvent) evt;
			if (e.state() == IdleState.READER_IDLE || e.state() == IdleState.WRITER_IDLE) {
				Logger.info("DibaSubagent IdleStateEvent triggered! e.state()={}", e.state().name());
				ctx.close();
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof IOException == false) {
			Logger.error("DibaSubagent Client ({}:{}) exceptionCaught:{} remoteAddress:{}", currentIpAddress, currentPort, cause.getMessage(), ctx.channel()
					.remoteAddress());
		}
		ctx.close();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		if (msg instanceof FullHttpResponse) {
			FullHttpResponse response = (FullHttpResponse) msg;

			if (msg instanceof HttpContent) {
				response.content().retain();
			}
			
			receivedMessageBlockingQueue.put(response);
			
			ctx.close();
		}
	}

	public ServerStatus getServerStatus() {
		ServerStatus serverStatus = null;
		FullHttpResponse receivedHttpMessage;
		try {
			receivedHttpMessage = receivedMessageBlockingQueue.poll(receiveTimeoutMilliSec, TimeUnit.MILLISECONDS);
			if (receivedHttpMessage != null && receivedHttpMessage.status() == HttpResponseStatus.OK) { // success

				HttpContent bodyContents = (HttpContent) receivedHttpMessage;
				ByteBuf contentBody = bodyContents.content();

				String bodyContentStr = contentBody.toString(CharsetUtil.UTF_8);
				if (bodyContentStr.isEmpty()) {
					Logger.error("GET Status Request {} Fail bodyContentStr is empty", receivedHttpMessage.toString());
				}
				serverStatus = gson.fromJson(bodyContentStr, ServerStatus.class);
				contentBody.release();
				
				serverStatus.setDate(new Date());

				return serverStatus;
			} else {
				Logger.error("GET Status Request Fail");
				return serverStatus;
			}
		} catch (InterruptedException e) {
			Logger.error("GET Status Request Fail : {} ", e);
			return serverStatus;
		}
	}
}
