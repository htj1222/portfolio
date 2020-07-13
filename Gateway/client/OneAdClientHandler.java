package com.castis.client;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.dto.AdResultCode;
import com.castis.dto.HttpResponseDto;
import com.castis.dto.constant.ConstantValue;
import com.castis.dto.enumeration.HttpContentType;
import com.castis.dto.enumeration.PlacementOpportunityTypeEnum;
import com.castis.dto.enumeration.ResponseType;
import com.castis.dto.request.AdRequest;
import com.castis.dto.response.AdResponse;
import com.castis.dto.response.CoreContent;
import com.castis.dto.response.OpportunityBinding;
import com.castis.dto.response.Placement;
import com.castis.dto.response.PlacementDecision;
import com.castis.dto.response.PlacementResponse;
import com.castis.dto.response.AdResponse.AdInfo;
import com.castis.util.OutputStreamToString;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OneAdClientHandler extends SimpleChannelInboundHandler<HttpObject> {

	static final Logger Logger = LogManager.getLogger(OneAdClientHandler.class);
	private Gson mGson = new GsonBuilder().create();

	private OneADClient oneAdClient;
	private final Channel inboundChannel;

	private HttpResponseDto responseDto;
	private PlacementResponse placementDecisionResponse;

	private HttpContentType responseContentType;

	public OneAdClientHandler(OneADClient oneAdClient) {
		this.oneAdClient = oneAdClient;
		this.inboundChannel = oneAdClient.getInboundChannel();

		// set default http response
		responseDto = new HttpResponseDto(HttpResponseStatus.INTERNAL_SERVER_ERROR, AdResultCode.INTERNAL_SERVER_ERROR_MESSAGE);

		// set default placementDecisionResponse
		AdRequest adRequest = oneAdClient.getAdRequest();
		responseContentType = HttpContentType.TEXT_PLAIN;

		if (adRequest == null) {
			Logger.error("[requestid=null] onead response fail. [internal server error]");
			sendHTMLResponseByHTTPResponseDto(false, responseDto);
		} else {
			placementDecisionResponse = new PlacementResponse();
			placementDecisionResponse.setDefaultMessage(adRequest.getRequestId(), adRequest.getMessageId());

			if (ResponseType.JSON == adRequest.getResponseType())
				responseContentType = HttpContentType.APPLICATION_JSON;
			else
				responseContentType = HttpContentType.APPLICATION_XML;
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		Logger.debug("onead handler start!");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (cause instanceof ReadTimeoutException) {
			Logger.error("[requestid={}] onead response fail. [read timeout]", oneAdClient.getAdRequest().getRequestId());
		} else {
			Logger.error("[requestid={}] onead response fail. [{}]", oneAdClient.getAdRequest().getRequestId(), cause.toString().toLowerCase());
		}
		sendConnectFailResponse();
		ctx.close();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent e = (IdleStateEvent) evt;
			if (e.state() == IdleState.READER_IDLE || e.state() == IdleState.WRITER_IDLE) {
				Logger.error("onead handler idle state event triggered! e.state()={}", e.state().name());
				ctx.close();
			}
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		long duration = System.currentTimeMillis() - oneAdClient.getSendSuccessTime();
		Logger.info("[requestid={}] onead response processing time {}ms.", placementDecisionResponse.getRequestId(), duration);

		if (msg instanceof HttpContent) {
			HttpContent bodyContents = (HttpContent) msg;
			String contentStr = bodyContents.content().toString(CharsetUtil.UTF_8);
			parsingAdResponseMessage(contentStr);
		}
		
		responseDto = convertPlacementDecisionToHttpResponse();
		sendHTMLResponseByHTTPResponseDto(false, responseDto);
	}

	private void parsingAdResponseMessage(String contentStr) {
		AdResponse adResponse = null;

		Logger.debug("parsingAdResponseMessage contentStr : {} ", contentStr);

		try {
			adResponse = mGson.fromJson(contentStr, AdResponse.class);
		} catch (Exception e) {
			Logger.error("[requestid={}] onead response fail. [parsing fail. e : {}, content : {}]", placementDecisionResponse.getRequestId(), e, contentStr.toString().toLowerCase());
		}

		if (adResponse == null) {
			Logger.error("[requestid={}] onead response fail. [parsing fail. content : {}]", placementDecisionResponse.getRequestId(), contentStr.toString().toLowerCase());
		} else {
			if (adResponse.getAdInfos() != null && adResponse.getAdInfos().size() != 0) {
				PlacementDecision 	placementDecision = new PlacementDecision();
				placementDecision.setId(ConstantValue.PLACEMENT_DECISION_RESPONSE_PLACEMENT_ID);
				
				OpportunityBinding	opportunitybinding = new OpportunityBinding();
				opportunitybinding.setOpportunityType(PlacementOpportunityTypeEnum.PREROLL2.toString());
				placementDecision.setOpportunitybinding(opportunitybinding);

				List<Placement> placementList = new ArrayList<Placement>();
				List<AdInfo> adInfoList = adResponse.getAdInfos();

				for (AdInfo adInfo : adInfoList) {
					Placement placement = new Placement();
					placement.setId(adInfo.getAdId());
					CoreContent content = new CoreContent();
					content.setAssetName(adInfo.getMediaFile().substring(adInfo.getMediaFile().lastIndexOf("/") + 1, adInfo.getMediaFile().length()));
					content.setTracking(adInfo.getTrackingId());
					placement.setContent(content);
					placementList.add(placement);
				}

				placementDecision.setPlacementList(placementList);
				placementDecisionResponse.setPlacementDecision(placementDecision);
				
				Logger.info("[requestid={}] onead response parsing success. {}", placementDecisionResponse.getRequestId(), placementDecisionResponse.toString().toLowerCase());
			} else {
				Logger.info("[requestid={}] onead response parsing success. no ad list. {}", placementDecisionResponse.getRequestId(), adResponse.toString().toLowerCase());
			}
		}
	}

	private HttpResponseDto convertPlacementDecisionToHttpResponse() {
		HttpResponseDto dto = new HttpResponseDto(HttpResponseStatus.INTERNAL_SERVER_ERROR, AdResultCode.INTERNAL_SERVER_ERROR_MESSAGE);

		if (oneAdClient.getAdRequest().getResponseType() == ResponseType.JSON) {
			dto.setResponseStatus(HttpResponseStatus.OK);
			dto.setResponseString(placementDecisionResponse.toJsonString());
		} else {
			try {
				OutputStream outputStream = new OutputStreamToString().outputStream;
				placementDecisionResponse.marshaling(outputStream);
				dto.setResponseStatus(HttpResponseStatus.OK);
				dto.setResponseString(outputStream.toString());
				outputStream.close();
			} catch (JAXBException e) {
				Logger.error("placement decision response marshaling fail {}", e.toString().toLowerCase());
				e.printStackTrace();
			} catch (IOException e) {
				Logger.error("placement decision response fail {}", e.toString().toLowerCase());
				e.printStackTrace();
			}
		}

		Logger.debug("placement decision response {}", dto.getResponseString());
		
		return dto;
	}

	private void sendHTMLResponseByHTTPResponseDto(boolean keepAlive, HttpResponseDto responseDto) {
		ByteBuf buffer = Unpooled.copiedBuffer(responseDto.getResponseString(), CharsetUtil.UTF_8);
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, responseDto.getResponseStatus(), Unpooled.wrappedBuffer(buffer));
		setResponseHeader(response, responseContentType);
		sendHttpResponse(keepAlive, response);
	}

	private void setResponseHeader(FullHttpResponse response, HttpContentType cType) {
		switch (cType) {
		case TEXT_PLAIN:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.TEXT_PLAIN);
			break;
		case TEXT_XML:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.TEXT_XML);
			break;
		case TEXT_HTML:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.TEXT_HTML);
			break;
		case APPLICATION_JSON:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.APPLICATION_JSON);
			break;
		case APPLICATION_XML:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.APPLICATION_XML);
			break;
		default:
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpContentType.TEXT_PLAIN);
			break;
		}
	}

	private void sendHttpResponse(boolean keepAlive, FullHttpResponse res) {
		res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes());
		if (keepAlive == false) {
			res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

			InetSocketAddress socketAddress = (InetSocketAddress) inboundChannel.remoteAddress();
		    InetAddress inetaddress = socketAddress.getAddress();
		    String vodIp = inetaddress.getHostAddress();
		    
			inboundChannel.writeAndFlush(res).addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					if (future.isSuccess()) {
						Logger.info("[requestid={}] vod({}) response success. {}", placementDecisionResponse.getRequestId(), vodIp, placementDecisionResponse.toString().toLowerCase());
					} else {
						Logger.error("[requestid={}] vod({}) response fail. {}", placementDecisionResponse.getRequestId(), vodIp, res.toString().toLowerCase());
						future.channel().close();
					}
					sendCloseOnFlush();
				}
			});
		}
	}

	private void sendCloseOnFlush() {
		if (inboundChannel != null && inboundChannel.isActive()) {
			inboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	public boolean sendConnectFailResponse() {
		responseDto = convertPlacementDecisionToHttpResponse();
		sendHTMLResponseByHTTPResponseDto(false, responseDto);
		return true;
	}

}