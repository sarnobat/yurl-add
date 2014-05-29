javascript:(function() {
	document.body.style.backgroundColor = "#AA0000";
	var x = new XMLHttpRequest();
	x.open('GET','http://localhost:8089/?param1=' +  encodeURIComponent(location.href) + '&title=' + encodeURIComponent(document.title),true);
	x.onreadystatechange = function() {
		if (x.readyState == 4) {
			if (x.status == 200) {
				window.open('', '_self', ''); window.close(); 
			} else {
				alert(x.status);}}};
				x.send();
})()
