package com.castis.adgateway;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.dto.HttpResponseDto;
import com.castis.dto.enumeration.HttpContentType;
import com.castis.http.HTTPResponse;
import com.castis.http.RestfulHttpResponseFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Sharable
public class HTTPHandler extends SimpleChannelInboundHandler<HttpObject> {
	static final Logger Logger = LogManager.getLogger(HTTPHandler.class);
	boolean mIsEncryption = false;
	static Gson mGson = new GsonBuilder().disableHtmlEscaping().create();

	public static final String ADGATEWAY_PREFIX = "/ADGateway";
	static final String TIMEOUT_SEC = "30";

	public HTTPHandler() throws Exception {
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		
		// channel handle
		if (msg instanceof HttpRequest) {
			HttpRequest request = (HttpRequest) msg;
						
			Logger.debug("request->({})", request);
			String requestUri = request.uri();
			
			if (requestUri.equals("/") == true) {
				sendHTMLResponseByContentString(ctx, request, HttpContentType.TEXT_PLAIN, makeCommandListStr());
				return;
			}
			if (requestUri.startsWith(ADGATEWAY_PREFIX) == false) {
				// this url is not mine
				sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_FOUND));
				return;
			}

			String restfulUrlStr = requestUri.substring(ADGATEWAY_PREFIX.length());
			if (restfulUrlStr.length() == 0) {
				// only ADGATEWAY_PREFIX
				sendHTMLResponseByContentString(ctx, request, HttpContentType.TEXT_PLAIN, makeCommandListStr());
			} else {
				if (request.method() == HttpMethod.GET) {
					
					// GET METHOD 
					HTTPResponse response = RestfulHttpResponseFactory.getHttpResponseObject(ctx, restfulUrlStr);
					HttpResponseDto responseDto = response.getHTTPResponse();
					
					if (responseDto != null)
						sendHTMLResponseByHTTPResponseDto(ctx, request, HttpContentType.TEXT_PLAIN, responseDto);
					
				} else if (request.method() == HttpMethod.POST) {
					Logger.debug("post requestUri->({})", requestUri);
					if (msg instanceof HttpContent) {
						// New chunk is received
						HttpContent bodyContents = (HttpContent) msg;
						ByteBuf contentBody = bodyContents.content();
						String bodyContentStr = contentBody.toString(CharsetUtil.UTF_8);
						HTTPResponse response = RestfulHttpResponseFactory.postHttpResponseObject(restfulUrlStr, bodyContentStr);
						// 현재 post는 /vodnotification만 처리함. 해당 응답은 xml로 content type이 필요하여 하드코딩함.
						sendHTMLResponseByHTTPResponseDto(ctx, request, HttpContentType.TEXT_XML, response.getHTTPResponse());
					}
				} else if (request.method() == HttpMethod.PUT) {
					// PUT METHOD
				} else {
					sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN));
				}
			}
			int urlDelimeterCount = requestUri.split("/").length;
			switch (urlDelimeterCount - 1) {
			case -1:
			case 0:
				sendHTMLResponseByContentString(ctx, request, HttpContentType.TEXT_PLAIN, makeCommandListStr());
				return;
			case 1:
				// one depth url
				if (requestUri.equals("/favicon.ico")) {
					sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN));
				}
				if (requestUri.equals("/") == true || requestUri.equals(ADGATEWAY_PREFIX) == true
						|| requestUri.equals(ADGATEWAY_PREFIX + "/") == true) {
					sendHTMLResponseByContentString(ctx, request, HttpContentType.TEXT_PLAIN, makeCommandListStr());
				}
				break;
			case 2:
				break;
			case 3:
				break;
			case 4:
				break;
			default:
				sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN));
				break;
			}
			return;
		}

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof IOException == false) {
			Logger.error("httphandler exception caught cause:{}", cause);
		}
		ctx.close();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent e = (IdleStateEvent) evt;
			if (e.state() == IdleState.READER_IDLE || e.state() == IdleState.WRITER_IDLE) {
				Logger.debug("HTTP IdleStateEvent triggered! e.state()=", e.state().name());
				ctx.close();
			}
		}
	}

	private void setResponseHeader(FullHttpResponse response, HttpContentType cType)
	{
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
	
	private void sendHTMLResponseByHTTPResponseDto(ChannelHandlerContext ctx, HttpRequest req, HttpContentType cType,
			HttpResponseDto responseDto) 
	{
		ByteBuf buffer = Unpooled.copiedBuffer(responseDto.getResponseString(), CharsetUtil.UTF_8);
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, responseDto.getResponseStatus(), Unpooled.wrappedBuffer(buffer));
		setResponseHeader(response, cType);
		sendHttpResponse(ctx, req, response);
	}
	
	private void sendHTMLResponseByContentString(ChannelHandlerContext ctx, HttpRequest req, HttpContentType cType,
			String PayloadStr) {
		ByteBuf buffer = Unpooled.copiedBuffer(PayloadStr, CharsetUtil.UTF_8);
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(buffer));
		setResponseHeader(response, cType);
		sendHttpResponse(ctx, req, response);
	}

	private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, FullHttpResponse res) {
		// Decide whether to close the connection or not.
		boolean keepAlive = HttpUtil.isKeepAlive(req);
		res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes());
		if (keepAlive == false) {
			res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.write(res);
			// If keep-alive is off, close the connection once the content is
			// fully written.
			ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		} else if (keepAlive == true) {
			// http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
			res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			res.headers().set("Keep-Alive", "timeout=" + TIMEOUT_SEC);
			ctx.writeAndFlush(res);
		}
		
	}

	private String makeCommandListStr() {
		String urlList = "ADGateway HTTP URL List\n\n";
		urlList += "/ADGateway/ping : check server is alive\n\n";
		return urlList;
	}
}
