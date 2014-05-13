$(document).ready(function() {
	
	$("#submit-job").click(function(){
		$.get("add", { email: $("#email").val(), cmd: $("#cmd").val(), cron: $("#cron").val() })
	})
})
