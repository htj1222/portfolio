package com.castis.http;

import io.netty.handler.codec.http.HttpResponseStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.dto.HttpResponseDto;

public class BadRequestResponse extends HTTPResponse {
	static final Logger Logger = LogManager.getLogger(BadRequestResponse.class);
	String currentRestfulUrl;
	
	public BadRequestResponse(String restfulHttpUrl) {
		currentRestfulUrl = restfulHttpUrl;
	}

	@Override
	public HttpResponseDto getHTTPResponse() {
		return new HttpResponseDto(HttpResponseStatus.BAD_REQUEST, "not valid url:(" + currentRestfulUrl + ")");
	}

}
