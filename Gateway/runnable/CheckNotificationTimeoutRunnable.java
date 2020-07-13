
package com.castis.runnable;

import java.util.List;

import io.netty.channel.ChannelHandler.Sharable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.NotificationAgent;

@Sharable
public class CheckNotificationTimeoutRunnable  implements Runnable {

	static final Logger Logger = LogManager.getLogger(CheckNotificationTimeoutRunnable.class);
	private int notificationTimeoutMilliSec;
	
	public CheckNotificationTimeoutRunnable(int notificationTimeoutMilliSec) {
		this.notificationTimeoutMilliSec = notificationTimeoutMilliSec;
	}
	
	@Override
	public void run() {
		List<String> willRemoveIds = NotificationAgent.getInstance().removeExceedTimeoutNotification(notificationTimeoutMilliSec);
		for (String requestId : willRemoveIds) {
			Logger.info("[requestid={}] notification remove in memory.", requestId);
			NotificationAgent.getInstance().removeNotification(requestId);
		}
	}
}
