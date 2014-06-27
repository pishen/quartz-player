$(document).ready(function() {
	$("#edit").click(function() {
		$(this).hide()
		$("#remove").hide()
		$("form button").prop("hidden", false)
		$("form input").prop("readonly", false)
		$("form input").prop("disabled", false)
	})
	$("#remove").click(function() {
		var req = {
			jobId : $("#job-id").text()
		}
		$.post("/remove", JSON.stringify(req), function() {
			window.location.pathname = "/"
		})
	})
})
