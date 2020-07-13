package com.castis.http;

import io.netty.channel.ChannelHandlerContext;

import com.castis.dto.enumeration.ResponseType;
import com.castis.http.get.GetPlacementDecisionResponse;
import com.castis.http.post.PostVODRequestNotifyResponse;

public class RestfulHttpResponseFactory {

	// get
	public static final String PLACEMENT_DECISION_REQUEST_XML = "/PlacementDecisionRequest";

	// post
	public static final String VOD_REQUEST_NOTIFY = "/vodnotification";

	static public HTTPResponse getHttpResponseObject(ChannelHandlerContext ctx, String getUrl) {
		if (getUrl.startsWith(PLACEMENT_DECISION_REQUEST_XML) == true) {
			return new GetPlacementDecisionResponse(ctx, getUrl, ResponseType.XML);
		} else {
			return new BadRequestResponse(getUrl);
		}
	}

	static public HTTPResponse postHttpResponseObject(String getUrl, String contentBodyStr) {
		if (getUrl.startsWith(VOD_REQUEST_NOTIFY) == true) {
			return new PostVODRequestNotifyResponse(getUrl, contentBodyStr);
		} else {
			return new BadRequestResponse(getUrl);
		}
	}
}
