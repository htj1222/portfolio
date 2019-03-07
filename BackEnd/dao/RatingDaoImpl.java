package com.castis.dao.elasticsearch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.castis.dao.RatingDao;
import com.castis.dto.Rating;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class RatingDaoImpl implements RatingDao {
	private static final Logger logger = LogManager.getLogger(RatingDaoImpl.class);
	static Gson mGson = new GsonBuilder().disableHtmlEscaping().create();
	String typeName = "rating";
	final static int SOURCE_BUILDER_SIZE = 5000;
	
	public RatingDaoImpl() {
		
	}
	
	public RatingDaoImpl(String name) {
		typeName = name;
	}

	@Override
	public void deleteAll() {
		ESImpl.deleteIndex(typeName);
	}

	@Override
	public long getCount() {
		return ESImpl.getCount(typeName);
	}
	
	@Override
	public long getCount(final String date) {
		SearchRequest searchRequest = new SearchRequest(typeName, typeName);
		SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
		sourceBuilder.size(SOURCE_BUILDER_SIZE);
		BoolQueryBuilder bqb = new BoolQueryBuilder();
		bqb.filter(QueryBuilders.matchQuery(ESImpl.TIME_STAMP, date));
		sourceBuilder.query(bqb);
		searchRequest.source(sourceBuilder);
		SearchResponse response = (SearchResponse) ESImpl.doQueryToElasticSearch(searchRequest);
		if (response == null) {
			return 0;
		}
		return response.getHits().totalHits;
	}

	@Override
	public void addRating(Rating ratingInfo) {
		if (ratingInfo != null) {
			JsonObject willInsertJsonObj = mGson.fromJson(mGson.toJson(ratingInfo), JsonObject.class);
			String timeStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(ratingInfo.getDate());
			willInsertJsonObj.addProperty(ESImpl.TIME_STAMP, timeStr);
			String docId = ratingInfo.getRegionName() + "_" + ratingInfo.getDate();
			IndexRequest indexRequest = new IndexRequest(typeName, typeName, docId)
					.source(mGson.toJson(willInsertJsonObj), XContentType.JSON);
			IndexResponse indexResponse = (IndexResponse) ESImpl.doQueryToElasticSearch(indexRequest);
			if (indexResponse == null) {
				return;
			}
			if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
				logger.debug("Index({}) document({}) CREATED", typeName, docId);
			} else {
				logger.debug("Index({}) document({}) result:({}) (UPDATED(1), DELETED(2), NOT_FOUND(3), NOOP(4)",
						indexResponse.getResult());
			}
		}
	}

	@Override
	public Rating getRating(final String date, final String regionName) {
		SearchRequest searchRequest = new SearchRequest(typeName, typeName);
		SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
		sourceBuilder.size(SOURCE_BUILDER_SIZE);
		BoolQueryBuilder bqb = new BoolQueryBuilder();
		bqb.filter(QueryBuilders.matchQuery(ESImpl.REGION_NAME, regionName));
		bqb.filter(QueryBuilders.matchQuery(ESImpl.TIME_STAMP, date));
		sourceBuilder.query(bqb);
		searchRequest.source(sourceBuilder);
		SearchResponse response = (SearchResponse) ESImpl.doQueryToElasticSearch(searchRequest);
		if (response == null) {
			return null;
		}
		List<Rating> results = new ArrayList<Rating>();
		List<SearchHit> searchHits = Arrays.asList(response.getHits().getHits());
		searchHits.forEach(hit -> results.add(mGson.fromJson(hit.getSourceAsString(), Rating.class)));
		if (results.size() != 0)
			return results.get(0);
		else
			return null;
	}
}
