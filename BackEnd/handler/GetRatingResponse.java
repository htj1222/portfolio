package com.castis.http.get;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.dao.RatingDao;
import com.castis.dao.elasticsearch.RatingDaoImpl;
import com.castis.dao.elasticsearch.RegionDaoImpl;
import com.castis.dto.HTTPResponseDto;
import com.castis.dto.Rating;
import com.castis.dto.Region;
import com.castis.http.HTTPResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GetRatingResponse extends HTTPResponse {
	static final Logger Logger = LogManager.getLogger(GetRatingResponse.class);
	static Gson mGson = new GsonBuilder().setDateFormat("yyyy-MM-dd").disableHtmlEscaping().create();
	String currentRestfulUrl;

	public GetRatingResponse(String restfulHttpUrl) {
		currentRestfulUrl = restfulHttpUrl;
	}

	@Override
	public HTTPResponseDto getHTTPResponse() {
		HTTPResponseDto response = new HTTPResponseDto(HttpResponseStatus.INTERNAL_SERVER_ERROR, "internal server error");
		Logger.info("RatingResponse process start!");
		
		if (currentRestfulUrl.length() == 0 || currentRestfulUrl.length() == 1) {
			response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
			response.setResponseString("not valid url:(" + currentRestfulUrl + ")");
		} else {
			QueryStringDecoder decoder = new QueryStringDecoder(currentRestfulUrl);
			
			if (decoder.parameters().get("regionName") == null) {
				response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
				response.setResponseString("regionName is null");
				return response;
			}
			if (decoder.parameters().get("requestDate") == null) {
				response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
				response.setResponseString("startDate is null");
				return response;
			}
			
			String regionName = decoder.parameters().get("regionName").get(0);
			String requestDate = decoder.parameters().get("requestDate").get(0);
			
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			Date start;
			try {
				start = df.parse(requestDate);
			} catch (ParseException e1) {
				response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
				response.setResponseString("date parsing fail " + e1);
				return response;
			}
			
			String startDateStr = df.format(start);

			RegionDaoImpl rdi = new RegionDaoImpl();
			Region regionInfo = rdi.get(regionName);
			if (regionInfo == null ) {
				response.setResponseStatus(HttpResponseStatus.NOT_FOUND);
				response.setResponseString("regionName " + regionName + " is not exist");
				return response;
			}
			
			RatingDao ratingDao = new RatingDaoImpl();
			Rating ratingInfo = ratingDao.getRating(startDateStr, regionInfo.getRegionName());
			
			if (ratingInfo == null) {
				response.setResponseStatus(HttpResponseStatus.NOT_FOUND);
				response.setResponseString("DailyStatus  "+ regionName + " is null");
			} else {
				response.setResponseStatus(HttpResponseStatus.OK);
				response.setResponseString(mGson.toJson(ratingInfo));
				Logger.info("URL:({}) response:({}) parsing success", currentRestfulUrl);
			}
		}
		return response;
	}

}
