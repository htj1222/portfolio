require.config({
    baseUrl: 'js/view'
});

define(["../region/region_select", "./chart", "./tooltip"],
	    function (regionSelect, chartManager, tooltip) {

		var page = {};
		
		page.init = function(container) {
			createPage(container);				
			regionSelect.setRegionInfo();
			chartManager.init();
			
			$("#searchButton").bind("click", searchButtonClick);
			
			// for test ///////////////////////////////////////////////////
			selectedRegionTeam = {"id":"테스트팀","text":"테스트팀","selected":true};
			selectedRegionKor = {"id":"테스트HFC","text":"테스트HFC","selected":true};
			selectedRegionServer = {"id":"175.117.194.197","text":"175.117.194.197","selected":true};
			
			var tempSelectedRegionTeam = [{"id":"테스트팀","text":"테스트팀","selected":true}];
			var tempSelectedRegionKor = [{"id":"테스트HFC","text":"테스트HFC","selected":true}];
			var tempSelectedRegionServer = [{"id":"175.117.194.197","text":"175.117.194.197","selected":true}];
			
			regionSelect.setTestTeam(tempSelectedRegionTeam, tempSelectedRegionKor, tempSelectedRegionServer);
			/////////////////////////////////////////////////////////////
		};

		function createPage(container){
			container.empty();
			
	    	var mainTemplate = new EJS({
	 			url : 'js/view/serverstatus/ejs/server_status.ejs'
	 		}).render();
	 		container.append(mainTemplate);
	 		
	 		var regionSelectDiv = $('#regionSelectDiv');
	 		var regionSelectEjs = new EJS({
	 			url : 'js/view/region/ejs/region.ejs'
	 		}).render({useDateRange:false, useSelectServer:true, useSelectServerSet:false});
	 		regionSelectDiv.append(regionSelectEjs);
		}
		
		function numberWithCommas(x) {
		    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
		}

		function searchButtonClick() {
			
			if (selectedRegionTeam.id == null ) {
				alert("팀 정보를 선택해 주세요");
				return;
			}
			if (selectedRegionKor.id == null ) {
				alert("분배센터 정보를 선택해 주세요");
				return;
			}
			if (selectedRegionServer.id == null ) {
				alert("서버 정보를 선택해 주세요");
				return;
			}
			
			$.ajax({
				type : "get",
				url : '/idas/getRegionName?teamName=' + selectedRegionTeam.id + '&regionKor=' + selectedRegionKor.id + '&serverIp=' + selectedRegionServer.id,
				dataType : "json",
				async: false,
				success : function(data) {
					selectedRegionName = data;
				} ,
				error : function() {
					alert(selectedRegionTeam.id + " " + selectedRegionKor.id + " 의 서버정보를 가져오는데 실패하였습니다.");
					return;
				}
			});
			
			$.ajax({
				type : "get",
				url : '/idas/serverStatus?regionName=' + selectedRegionName,
				dataType : "json",
				async: false,
				success : function(data) {
					if (data[0] != null) {
						var inputTrafficTotal = 0;
						var outputTrafficTotal = 0;

						var serverStatus = data[0];
						
						//Hardware Status 차트 데이터 갱신
						chartManager.initCpuUsageChart(serverStatus.cpuUsage.toFixed(2));
						chartManager.initMemUsageChart(serverStatus.memUsage.toFixed(2));
						chartManager.initLogDiskUsageChart(serverStatus.logDiskPercent.toFixed(2));
						chartManager.initOsDiskUsageChart(serverStatus.osDiskPercent.toFixed(2));
						
						//Hardware Status에 데이터 갱신 시간 설정
						var statusDate = new Date(serverStatus.date);
						var datetime = statusDate.getFullYear()  + "/"
		                + (statusDate.getMonth()+1)  + "/" 
		                +  statusDate.getDate() + " "  
		                + statusDate.getHours() + ":"  
		                + statusDate.getMinutes() + ":" 
		                + statusDate.getSeconds();
						$("#hardwareStatusTime").text(datetime);
						
						//interFaceStatus 항목에 nic별로 데이터 출력
						var interFaceStatusDiv = $('#interFaceStatusDiv');
						interFaceStatusDiv.empty();
						var interfaces = data[0].interfaces;
						$(interfaces).each(function(n, interfaceInfo) {
							if (interfaceInfo.component == "transcaster" ) {
								if (interfaceInfo.inputOrOutput == "input") {
									inputTrafficTotal += interfaceInfo.input;
								} else {
									outputTrafficTotal += interfaceInfo.output;
								}
							}
							tooltip.addInterfaceTooltip(interFaceStatusDiv, interfaceInfo);
						});
						
						//input, output traffic 데이터 출력
						$("#inputTrafficTotal").text(numberWithCommas(inputTrafficTotal) + " kb");
						$("#outputTrafficTotal").text(numberWithCommas(outputTrafficTotal) + " kb");
						
						// processMonitoring 항목 데이터 출력
						var processMonitoringDiv = $('#processMonitoringDiv');
						processMonitoringDiv.empty();
						var processStatus = data[0].processStatus;
						$(processStatus).each(function(n, processInfo) {
							tooltip.addProcessTooltip(processMonitoringDiv, processInfo);
						});
					}
				} ,
				error : function() {
					alert(selectedRegionTeam.id + " " + selectedRegionKor.id + " 의 서버 상태 정보를 가져오는데 실패하였습니다.");
					return;
				}
			});
			

			$.ajax({
				type : "get",
				url : '/idas/trafficStatus?regionName=' + selectedRegionName,
				dataType : "json",
				async: false,
				success : function(data) {
					var chartData = [];
					
					// traffic 데이터 차트
					$(data).each(function(n, trafficStatus) {
						if (trafficStatus.totalInputTraffic != null && trafficStatus.totalOutputTraffic != null && trafficStatus.date != null ) {
							chartData.push({
								date : trafficStatus.date,
								totalInputTraffic : trafficStatus.totalInputTraffic,
								totalOutputTraffic : trafficStatus.totalOutputTraffic,
							});
							
						}
					});
					
					// 받은 데이터를 일자 별로 정렬
					chartData = chartData.sort(function(a,b) {
						return a.date < b.date ? -1 : a.date > b.date ? 1 : 0;
					});
					
					chartManager.updateTrafficChartData(chartData);
				} ,
				error : function() {
					alert(selectedRegionTeam.id + " " + selectedRegionKor.id + "의 트래픽 정보를 가져오는데 실패하였습니다.");
					return;
				}
			});
			
			getAlarmHistory();
		}
		
		function getAlarmHistory() {
			var today = moment();
			var startDate = moment().subtract(1, 'days');
			var endDate = today;
			
			$.ajax({
				type : "get",
				url : '/idas/alarmHistory?regionName=' + selectedRegionName + "&startDate=" + startDate.format('YYYY-MM-DD') + "&endDate=" + endDate.format('YYYY-MM-DD'),
				dataType : "json",
				async: false,
				success : function(data) {
					parsingFailoverMessage(data, "failoverHistoryDiv");
				} ,
				error : function() {
					alert(selectedRegionTeam.id + " " + selectedRegionKor.id + "의 Failover 정보를 가져오는데 실패하였습니다.");
					return;
				}
			});
		}
		
		function parsingFailoverMessage(data, component){
			var currentdate = new Date(); 
			var datetime = currentdate.getFullYear()  + "/"
			                + (currentdate.getMonth()+1)  + "/" 
			                +  currentdate.getDate() + " "  
			                + currentdate.getHours() + ":"  
			                + currentdate.getMinutes() + ":" 
			                + currentdate.getSeconds();
			
			var array = data.split(/\n/);
			var today = moment().format('YYYY-MM-DD');
			var yesterday = moment().subtract(1, 'days').format('YYYY-MM-DD');
			var newHtml = "";
			
			var yesterDayCriticalCount = 0;
			var yesterDayWarningCount = 0;
			var criticalCount = 0;
			var warningCount = 0;
			
			for(var i = array.length - 1; i>=0; i--){
				var strArray = array[i].split(" ");
				var date = strArray[2] + " " + strArray[3];
				var history = "";
				
				for(var k=4; k<strArray.length; k++){
					history += " "  + strArray[k];
				}
				if(date == "" || history == "")
					continue;
				
				// timeline add
				newHtml += "<div class=\"feed-element\">"
				if(history.search("Clear") != -1){
					newHtml += "<span class=\"label pull-right label-info\">Clear Issue</span>";
				}else if(history.search("terminated") != -1){
					if(strArray[2] == today){
						criticalCount++;
					} 
					if(strArray[2] == yesterday){
						yesterDayCriticalCount++;
					}
					newHtml += "<span class=\"label pull-right label-danger\">Critical Issue</span>";
				}else{
					if(strArray[2] == today){
						warningCount++;
					} 
					if(strArray[2] == yesterday){
						yesterDayWarningCount++;
					}
					newHtml += "<span class=\"label pull-right label-warning\">Warning Issue</span>";
				}
				
				newHtml += "<div>" + history + "</div>"
				newHtml += "<small class=\"text-muted\">" + date + "</small>"
				newHtml += "</div></div>";
			}
			
			if(newHtml == ""){
				newHtml += "<div class=\"feed-element\">"
				newHtml += "<span class=\"label pull-right label-info\">Clear Issue</span>";
				newHtml += "<div>발생한 이슈가 없습니다.</div>"
				newHtml += "<small class=\"text-muted\">" + datetime + "</small>"
				newHtml += "</div></div>";
			}

			if (criticalCount + yesterDayCriticalCount + warningCount + yesterDayWarningCount == 0) {
				$('#failoverInfo').text("");
			} else if (warningCount + yesterDayWarningCount == 0) {
				$('#failoverInfo').text((criticalCount + yesterDayCriticalCount)  + "번의 Critical Issue가 발생하였습니다. ");
			} else if (criticalCount + yesterDayCriticalCount == 0) {
				$('#failoverInfo').text((warningCount + yesterDayWarningCount) + "번의 Warning Issue가 발생하였습니다.")
			} else {
				$('#failoverInfo').text((criticalCount + yesterDayCriticalCount) + "번의 Critical Issue가 발생하였습니다. "
						+ (warningCount + yesterDayWarningCount) + "번의 Warning Issue가 발생하였습니다.")
			}
			
			$('#todayIssueCount').text((criticalCount + warningCount));
		
			var temp = (criticalCount + warningCount) - (yesterDayCriticalCount + yesterDayWarningCount);
			if (temp == 0) {
				$('#compareYesterdayIssueCount').html((yesterDayCriticalCount + yesterDayWarningCount));
			} else if (temp > 0) {
				$('#compareYesterdayIssueCount').html((yesterDayCriticalCount + yesterDayWarningCount) + "<i class=\"fa fa-level-up\"></i>");
			} else {
				$('#compareYesterdayIssueCount').html((yesterDayCriticalCount + yesterDayWarningCount) + "<i class=\"fa fa-level-down\"></i>");
			}
			
			$('#' + component).html(newHtml);	
		}
	    return page;
});
