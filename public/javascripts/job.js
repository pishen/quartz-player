$(document).ready(function() {
	$("#remove").click(function() {
		var req = {
			jobId : $("#job-id").text()
		}
		$.post("/remove", JSON.stringify(req), function() {
			window.location.pathname = "/"
		})
	})
})
