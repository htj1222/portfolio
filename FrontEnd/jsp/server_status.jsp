<!DOCTYPE html>
<%@page import="org.springframework.web.servlet.LocaleResolver"%>
<%@page import="javax.naming.spi.Resolver"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<html>

<head>

<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">

<title>ServerStatus</title>

<link href="inspinia/css/bootstrap.min.css" rel="stylesheet">
<link href="inspinia/font-awesome/css/font-awesome.css" rel="stylesheet">

<link href="inspinia/css/animate.css" rel="stylesheet">
<link href="inspinia/css/style.css" rel="stylesheet">
<link href="inspinia/css/tooltip-classic.css" rel="stylesheet">

<link href="inspinia/css/plugins/select2/select2.min.css"
	rel="stylesheet">

</head>

<body>

	<div id="serverStatusPage"></div>

	<!-- Mainly scripts -->
	<script src="inspinia/js/jquery-2.1.1.js"></script>
	<script src="inspinia/js/bootstrap.min.js"></script>
	<script src="inspinia/js/plugins/metisMenu/jquery.metisMenu.js"></script>
	<script src="inspinia/js/plugins/slimscroll/jquery.slimscroll.min.js"></script>

	<!-- Date range use moment.js same as full calendar plugin -->
	<script src="inspinia/js/plugins/fullcalendar/moment.min.js"></script>

	<!-- Peity -->
	<script src="inspinia/js/plugins/peity/jquery.peity.min.js"></script>
	<script src="inspinia/js/demo/peity-demo.js"></script>

	<!-- Select2 -->
	<script src="channel_asset/js/select2.full.min.js"></script>

	<!-- Custom and plugin javascript -->
	<script src="inspinia/js/inspinia.js"></script>
	<script src="inspinia/js/plugins/pace/pace.min.js"></script>

	<!-- jQuery UI -->
	<script src="inspinia/js/plugins/jquery-ui/jquery-ui.min.js"></script>

	<!-- chart UI -->
	<script src="js/plugins/amcharts/amcharts.js"></script>
	<script src="js/plugins/amcharts/pie.js"></script>
	<script src="js/plugins/amcharts/serial.js"></script>
	<script src="js/plugins/amcharts/plugins/export/export.min.js"></script>

	<script type="text/javascript" src="js/lib/ejs_production.js"></script>
	<script type="text/javascript" src="js/lib/ejs.js"></script>

	<!-- main js -->
	<script data-main="js/view/serverstatus/server_status_main"
		src="js/lib/require.js"></script>

</body>
</html>
