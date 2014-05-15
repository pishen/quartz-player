$(document).ready(function() {
	$("#submit-job").click(function() {
		var req = {
			id: $("#job-id").val(), 
			email: $("#email").val(),
			cmd: $("#cmd").val(),
			cron: $("#cron").val()
		}
		$.post("/add", JSON.stringify(req), function() {
			window.location.reload()
		})
	})
})
