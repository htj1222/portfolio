package com.castis.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.dto.notification.Notification;

public class NotificationAgent {

	private volatile static NotificationAgent singleton;

	static final Logger Logger = LogManager.getLogger(NotificationAgent.class);

	private Map<String, Notification> notificationMap = new HashMap<String, Notification>();
	private Map<String, Long> notificationTimeoutCheckMap = new HashMap<String, Long>();

	public static NotificationAgent getInstance() {
		if (singleton == null) {
			synchronized (NotificationAgent.class) {
				if (singleton == null) {
					singleton = new NotificationAgent();
				}
			}
		}
		return singleton;
	}

	public NotificationAgent() {
		notificationMap.clear();
		notificationTimeoutCheckMap.clear();
	}

	public boolean putNotification(Notification notification) {
		Logger.info("[requestid={}] notification save in memory.", notification.getVodRequestId());
		if (isExist(notification.getVodRequestId())) {
			Logger.info("[requestid={}] notification save fail in memory. already exist.", notification.getVodRequestId());
			return false;
		}
		synchronized (notificationMap) {
			notificationMap.put(notification.getVodRequestId(), notification);
		}
		synchronized (notificationTimeoutCheckMap) {
			notificationTimeoutCheckMap.put(notification.getVodRequestId(), System.currentTimeMillis());
		}
		return true;
	}

	public Notification getNotification(final String vodRequestId) {
		synchronized (notificationMap) {
			return notificationMap.get(vodRequestId);
		}
	}

	public Boolean isExist(final String vodRequestId) {
		return notificationMap.containsKey(vodRequestId);
	}

	public void removeNotification(final String vodRequestId) {
		if (vodRequestId.isEmpty() == false) {
			synchronized (notificationMap) {
				notificationMap.remove(vodRequestId);
			}
			synchronized (notificationTimeoutCheckMap) {
				notificationTimeoutCheckMap.remove(vodRequestId);
			}
		}
	}

	public List<String> removeExceedTimeoutNotification(long timeOutMilliSec) {
		List<String> willRemoveIds = new ArrayList<String>();
		long currentTime = System.currentTimeMillis();
		synchronized (notificationTimeoutCheckMap) {
			for (Map.Entry<String, Long> elem : notificationTimeoutCheckMap.entrySet()) {
				long time = currentTime - elem.getValue();
				Logger.debug("[TEST] time {} / {} ", time, timeOutMilliSec);
				if (time >= timeOutMilliSec) {
					willRemoveIds.add(elem.getKey());
				}
			}
		}

		return willRemoveIds;
	}
	
}
