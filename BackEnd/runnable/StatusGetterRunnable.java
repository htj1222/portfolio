package com.castis.runnable;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.DibaSubagentAgent;
import com.castis.client.DibaSubagentClient;
import com.castis.dao.RegionDao;
import com.castis.dao.StatusDao;
import com.castis.dao.elasticsearch.RegionDaoImpl;
import com.castis.dao.elasticsearch.StatusDaoImpl;
import com.castis.dto.Region;
import com.castis.dto.ServerStatus;

public class StatusGetterRunnable implements Runnable {
	static final Logger Logger = LogManager.getLogger(StatusGetterRunnable.class);

	public StatusGetterRunnable() {
	}
	
	@Override
	public void run() {
		StatusDao statusDao = new StatusDaoImpl();
		RegionDao regionDao = new RegionDaoImpl();
		
		List<Region> regionList = regionDao.getAll();
		Logger.info("Status getter job start region size ({})", regionList.size());
		
		for (Region region : regionList) {
			DibaSubagentClient client = DibaSubagentAgent.getInstance().createDibaSubagentClient(region);
			if (client == null) {
				Logger.error("Status getter job error dibaSubagent({}:{}) is not exist client", region.getDibaHWCheckerIp(), region.getDibaSubagentPort());
				continue;
			}
			
			if (client.getHTTPChannel() == null) {
				Logger.error("Status getter job error dibaSubagent({}:{}) is connection fail", region.getDibaHWCheckerIp(), region.getDibaSubagentPort());
				continue;
			}
			
			ServerStatus status = client.getStatus();
			if (status == null) {
				Logger.error("Status getter job error dibaSubagent({}:{}) status is null", region.getDibaHWCheckerIp(), region.getDibaSubagentPort());
				continue;
			}
			
			status.setTotalTraffic();
			statusDao.addStatus(status, new Date(), region.getRegionName());
			
			Logger.info("Status getter job success dibaSubagent({}:{}).", region.getDibaHWCheckerIp(), region.getDibaSubagentPort());
		}
	}
}
