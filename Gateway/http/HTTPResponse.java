package com.castis.http;

import com.castis.dto.HttpResponseDto;


public abstract class HTTPResponse {
	protected HttpResponseDto responseDto = null;
	protected String mBodyContents;
	
	public abstract HttpResponseDto getHTTPResponse();
	
	public String getBodyContents() {
		return mBodyContents;
	}

	public void setBodyContents(String bodyContents) {
		this.mBodyContents = bodyContents;
	}
}
