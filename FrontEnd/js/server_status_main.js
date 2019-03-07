require.config({
	baseUrl : 'js/view'
});

require([ './serverstatus/server_status_page' ], function(remote) {

	function init() {
		createMainStage();
	}

	function createMainStage() {
		var container = $('#serverStatusPage');
		remote.init(container);
	}

	init();
});
