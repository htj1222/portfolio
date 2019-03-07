package com.castis.idas.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.castis.idas.connector.BackEndConnector;
import com.castis.idas.dto.backend.Region;
import com.castis.idas.dto.backend.RegionList;
import com.castis.idas.service.BackEndService;

@Service
public class RegionAgent {

	@Autowired
	BackEndService backEndService;

	private static Log log = LogFactory.getLog(BackEndConnector.class);

	// key - 품질솔루션 팀 , <Map> key - hfc명, value - hfc 내부 서버 리스트
	private Map<String, Map<String, List<Region>>> regionMap;

	@PostConstruct
	public void init() {
		if (regionMap == null || regionMap.isEmpty() == true) {
			RegionList regionList = backEndService.getRegionAll();
			if (regionList == null)
				log.error("RegionAgent region list is null");
			else
				setRegionAgent(regionList.getRegionList());
		}
	}

	public void setRegionAgent(List<Region> regionList) {
		regionMap = new HashMap<String, Map<String, List<Region>>>();
		regionMap.clear();
		for (Region region : regionList) {
			// 품솔팀 정보가 없을 경우
			if (regionMap.get(region.getQualitySoulutionTeam()) == null) {
				List<Region> list = new ArrayList<Region>();
				list.add(region);
				Map<String, List<Region>> map = new HashMap<String, List<Region>>();
				map.put(region.getRegionKor(), list);
				regionMap.put(region.getQualitySoulutionTeam(), map);
			} else {
				// hfc 정보가 없을 경우
				if (regionMap.get(region.getQualitySoulutionTeam()).get(region.getRegionKor()) == null) {
					List<Region> list = new ArrayList<Region>();
					list.add(region);
					regionMap.get(region.getQualitySoulutionTeam()).put(region.getRegionKor(), list);
				} else {
					List<Region> list = regionMap.get(region.getQualitySoulutionTeam()).get(region.getRegionKor());
					list.add(region);
				}
			}
		}
	}

	public Map<String, Map<String, List<Region>>> getRegionMap() {
		if (regionMap == null || regionMap.isEmpty() == true) {
			init();
		}
		return regionMap;
	}

	public void setRegionMap(Map<String, Map<String, List<Region>>> regionMap) {
		this.regionMap = regionMap;
	}

	public void printRegionConfig() {
		if (regionMap != null) {
			for (Map<String, List<Region>> tempRegionMap : regionMap.values()) {
				for (List<Region> regionList : tempRegionMap.values()) {
					for (Region region : regionList) {
						log.info("region Info : " + region);
					}
				}
			}
		}
		log.info(regionMap);
	}

	public Map<String, List<Region>> getRegionListByTeam(String qualitySoulutionTeam) {
		if (getRegionMap() != null) {
			return regionMap.get(qualitySoulutionTeam);
		}
		return null;
	}

	public Region getRegionInfo(String regionName) {
		if (getRegionMap() != null) {
			for (Map<String, List<Region>> tempRegionMap : regionMap.values()) {
				for (List<Region> regionList : tempRegionMap.values()) {
					for (Region region : regionList) {
						if (region.getRegionName().equalsIgnoreCase(regionName))
							return region;
					}
				}
			}
		}
		return null;
	}

	public List<String> getRegionTeamList() {
		if (getRegionMap() != null) {
			List<String> list = new ArrayList<String>();
			for (String key : regionMap.keySet()) {
				list.add(key);
			}
			return list;
		}
		return null;
	}

	public List<String> getRegionKorList(String teamName) {
		if (getRegionMap() != null) {
			List<String> list = new ArrayList<String>();
			Map<String, List<Region>> temp = regionMap.get(teamName);
			for (String korName : temp.keySet()) {
				list.add(korName);
			}
			return list;
		}
		return null;
	}

	public List<Region> getRegionServerList(String teamName, String regionKor) {
		if (getRegionMap() != null) {
			if (teamName.isEmpty()) {
				for (String key : regionMap.keySet()) {
					Map<String, List<Region>> temp = regionMap.get(key);
					if (temp.get(regionKor) != null) {
						return temp.get(regionKor);
					}
				}
			} else {
				if (regionMap.get(teamName) != null)
					return regionMap.get(teamName).get(regionKor);
			}
		}
		return null;
	}

	public String getRegionName(String teamName, String regionKor, String serverIp) {
		if (getRegionMap() != null) {
			if (regionMap.get(teamName) != null) {
				List<Region> list = regionMap.get(teamName).get(regionKor);
				for (Region region : list) {
					if (region.getReportIp().equalsIgnoreCase(serverIp))
						return region.getRegionName();
				}
			}
		}
		return null;
	}
}
