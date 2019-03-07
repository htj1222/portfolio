package com.castis.idas.controller;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.castis.idas.common.define.Constants;
import com.castis.idas.common.idgenerator.IdGenerator;
import com.castis.idas.common.utils.LogString;
import com.castis.idas.dto.TransactionID;
import com.castis.idas.service.BackEndService;
import com.castis.idas.setting.Properties;
import com.mangofactory.swagger.annotations.ApiIgnore;

@Controller
public class ServerStatusController {

	private String jspName = "empty";
	private static Log log = LogFactory.getLog(ServerStatusController.class);

	@Autowired
	Properties properties;

	@Autowired
	BackEndService backEndService;

	@RequestMapping(value = { "status" }, method = RequestMethod.GET)
	@ApiIgnore
	public String status(HttpServletRequest request) {
		try {
			TransactionID trId = new TransactionID(TransactionID.TRANSACTION_ID_TYPE, IdGenerator.getInstance().generateId());

			long startTime = System.currentTimeMillis();
			log.info(LogString.logCommonRequest(request, Constants.request.GET, trId));
			jspName = "/status/server_status";

			log.info(LogString.logCommonResponse(startTime, Constants.request.GET, trId));
		} catch (Exception e) {
			log.error("", e);

			request.setAttribute("errorCode", "500");
			jspName = "/common/errorPage";
		}
		return jspName;
	}

	@RequestMapping(value = "/serverStatus", method = RequestMethod.GET, produces = "text/plain;charset=UTF-8")
	@ResponseBody
	public String serverStatus(@RequestParam("regionName") String regionName) {
		return backEndService.getServerStatus(regionName);
	}

	@RequestMapping(value = "/alarmHistory", method = RequestMethod.GET, produces = "text/plain;charset=UTF-8")
	@ResponseBody
	public String alarmHistory(@RequestParam("regionName") String regionName, @RequestParam("startDate") String startDate,
			@RequestParam("endDate") String endDate) {
		return backEndService.getAlarmHistory(regionName, startDate, endDate);
	}

	@RequestMapping(value = "/trafficStatus", method = RequestMethod.GET, produces = "text/plain;charset=UTF-8")
	@ResponseBody
	public String trafficStatus(@RequestParam("regionName") String regionName) {
		return backEndService.getTrafficStatus(regionName);
	}
}
