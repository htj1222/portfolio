package com.castis.agent;

import io.netty.channel.EventLoopGroup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.client.DibaSubagentClient;
import com.castis.dto.Region;

public class DibaSubagentAgent {
	static final Logger Logger = LogManager.getLogger(DibaSubagentAgent.class);
	private volatile static DibaSubagentAgent singleton;

	public static DibaSubagentAgent getInstance() {
		if (singleton == null) {
			synchronized (DibaSubagentAgent.class) {
				if (singleton == null) {
					singleton = new DibaSubagentAgent();
				}
			}
		}
		return singleton;
	}

	private EventLoopGroup DibaSubagentEventLoopGroup;

	public EventLoopGroup getDibaSubagentEventLoopGroup() {
		return DibaSubagentEventLoopGroup;
	}

	public void setDibaSubagentEventLoopGroup(EventLoopGroup DibaSubagentEventLoopGroup) {
		this.DibaSubagentEventLoopGroup = DibaSubagentEventLoopGroup;
	}

	public void shutdownGracefully() {
		if (DibaSubagentEventLoopGroup != null) {
			DibaSubagentEventLoopGroup.shutdownGracefully();
			DibaSubagentEventLoopGroup = null;
		}
	}

	public DibaSubagentClient createDibaSubagentClient(Region region) {
		DibaSubagentClient client = new DibaSubagentClient(region);
		if (client.getHTTPChannel() == null)
			return null;
		return client;
	}
	
}
