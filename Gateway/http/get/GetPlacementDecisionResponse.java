package com.castis.http.get;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.ConfigAgent;
import com.castis.agent.DnsResolveAgent;
import com.castis.agent.NotificationAgent;
import com.castis.client.OneADClient;
import com.castis.dto.AdResultCode;
import com.castis.dto.ConfigDto;
import com.castis.dto.HttpResponseDto;
import com.castis.dto.constant.ConstantValue;
import com.castis.dto.enumeration.ResponseType;
import com.castis.dto.exception.CiRuntimeException;
import com.castis.dto.notification.Notification;
import com.castis.dto.request.AdRequest;
import com.castis.dto.request.PlacementDecision;
import com.castis.dto.response.PlacementResponse;
import com.castis.http.HTTPResponse;
import com.castis.util.OutputStreamToString;

public class GetPlacementDecisionResponse extends HTTPResponse {
	static final Logger Logger = LogManager.getLogger(GetPlacementDecisionResponse.class);

	String currentRestfulUrl;
	ResponseType responseType;
	ChannelHandlerContext ctx;
	Channel outboundChannel;

	public GetPlacementDecisionResponse(ChannelHandlerContext ctx, String restfulHttpUrl, ResponseType responseType) {
		this.ctx = ctx;
		this.currentRestfulUrl = restfulHttpUrl;
		this.responseType = responseType;
	}

	@Override
	public HttpResponseDto getHTTPResponse() {
		Logger.debug("Get URL:({}) process start", currentRestfulUrl);

		InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
	    InetAddress inetaddress = socketAddress.getAddress();
	    String clientIp = inetaddress.getHostAddress(); // IP address of client
	    
		String requestId = "";
		String sessionId = "";
		try {
			// 요청온 uri를 parsing하여 유효한지 체크 유효하지 않다면 CiRuntimeException 을 던진다.
			PlacementDecision placementDecisionRequestValue = parsingRequest();
			requestId = placementDecisionRequestValue.getRequestId();
			sessionId = placementDecisionRequestValue.getMessageId();

			Logger.info("[requestid={}] vod({}) request pasing success. {}", requestId, clientIp, placementDecisionRequestValue.toString().toLowerCase());
			
			// OneAd에 요청할 adRequest로 변환 유효하지 않다면 CiRuntimeException 을 던진다.
			AdRequest adRequest = convertRequest(placementDecisionRequestValue);

			// OneAD에 요청할 client 생성, 응답처리는 OneADClient에 전달한 channel에서 한다.
			makeOneAdClient(adRequest);

		} catch (CiRuntimeException e) {
			Logger.error("[requestid={}] vod({}) request pasing fail. ciruntime exception {}", requestId, clientIp, e.getMessage().toString().toLowerCase());
			return makeFailResponse(responseType, requestId, sessionId);
		} catch (Exception e) {
			Logger.error("[requestid={}] vod({}) request pasing fail. exception {}", requestId, clientIp, e.getMessage().toString().toLowerCase());
			return makeFailResponse(responseType, requestId, sessionId);
		}

		// 처리에 성공하면 ADClient에서 http의 응답을 처리하므로 null을 보낸다.
		// 처리에 실패하는 경우 실패 메시지를 위에서 반환한다.
		return null;
	}

	static public HttpResponseDto makeFailResponse(ResponseType responseType, String requestId, String sessionId) {
		PlacementResponse placementDecisionResponse = new PlacementResponse();
		if (requestId.isEmpty() == false && sessionId.isEmpty() == false) {
			placementDecisionResponse.setRequestId(requestId);
			placementDecisionResponse.setSessionId(sessionId);
		}

		String responseStr = "";
		if (responseType == ResponseType.JSON) {
			responseStr = placementDecisionResponse.toJsonString();
		} else {
			OutputStream outputStream = new OutputStreamToString().outputStream;

			try {
				placementDecisionResponse.marshaling(outputStream);
				responseStr = outputStream.toString();
			} catch (JAXBException e) {
				Logger.error("placement decision response marshaling fail {}", e.toString().toLowerCase());
				return new HttpResponseDto(HttpResponseStatus.INTERNAL_SERVER_ERROR, AdResultCode.INTERNAL_SERVER_ERROR_MESSAGE);
			}
		}

		Logger.debug("makeFailResponse {}", responseStr);
		return new HttpResponseDto(HttpResponseStatus.OK, responseStr);
	}

	public PlacementDecision parsingRequest() throws Exception {
		PlacementDecision placementDecisionRequestValue = new PlacementDecision();

		if (currentRestfulUrl.length() == 0 || currentRestfulUrl.length() == 1) {
			throw new CiRuntimeException("[adDecisionRequest] does NOT exist. not valid url", AdResultCode.INVALID_REQUEST_ERROR_CODE);
		}

		QueryStringDecoder decoder = new QueryStringDecoder(currentRestfulUrl);

		if (decoder.parameters().get("VOD_Request_ID") == null || decoder.parameters().get("VOD_Request_ID").isEmpty()) {
			throw new CiRuntimeException("[adDecisionRequest] VOD_Request_ID does NOT exist. not valid url", AdResultCode.INVALID_REQUEST_ERROR_CODE);
		}
		if (decoder.parameters().get("VOD_Session_ID") == null || decoder.parameters().get("VOD_Session_ID").isEmpty()) {
			throw new CiRuntimeException("[adDecisionRequest] messageId does NOT exist. not valid url", AdResultCode.INVALID_REQUEST_ERROR_CODE);
		}

		// required value
		placementDecisionRequestValue.setRequestId(decoder.parameters().get("VOD_Request_ID").get(0));
		placementDecisionRequestValue.setAdvPlatformType("VOD");
		placementDecisionRequestValue.setOpportunityType("Pre-Roll");
		placementDecisionRequestValue.setMessageId(decoder.parameters().get("VOD_Session_ID").get(0));

		return placementDecisionRequestValue;
	}

	public AdRequest convertRequest(PlacementDecision placementDecisionRequestValue) {
		AdRequest adRequest = null;

		Notification notification = NotificationAgent.getInstance().getNotification(placementDecisionRequestValue.getRequestId());
		if (notification == null) {
			throw new CiRuntimeException("[VodRequestDescription] VOD_Request_ID (" + placementDecisionRequestValue.getRequestId()
					+ ") does NOT exist in memory", AdResultCode.REQUEST_DESCRIPTION_NOT_FOUND_CODE);
		}

		// as-is code
		if (placementDecisionRequestValue.getMessageId().equalsIgnoreCase(ConstantValue.INITIALIZE_MESSAGE_ID)) {
			throw new CiRuntimeException("INITIALIZE_MESSAGE", AdResultCode.SUCCESS_CODE);
		}

		try {
			adRequest = new AdRequest(notification);
			adRequest.setRequestId(placementDecisionRequestValue.getRequestId());
			adRequest.setMessageId(placementDecisionRequestValue.getMessageId());
			adRequest.setResponseType(responseType);
		} catch (UnsupportedEncodingException e) {
			throw new CiRuntimeException("convert placementDecisionRequest to AdRequest fail", AdResultCode.INTERNAL_SERVER_ERROR_CODE);
		}

		return adRequest;
	}

	public void makeOneAdClient(AdRequest adRequest) {
		ConfigDto config = ConfigAgent.getInstance().getConfigDto();
		final Channel inboundChannel = ctx.channel();

		String oneAdIp = config.getOneAdIp();
		if (config.isOneAdIpIsDns()) {
			long current = System.currentTimeMillis();
			oneAdIp = DnsResolveAgent.getInstance().getIp(config.getOneAdIp());
			long duration = System.currentTimeMillis() - current;
			Logger.error("onead response processing time {}ms.", duration);
		}
		Logger.debug("OneAD ip {}:{} req: {}", oneAdIp, config.getOneAdPort(), adRequest.toJsonStr());
		new OneADClient(oneAdIp, config.getOneAdPort(), inboundChannel, adRequest);
	}
}
