<html>
<head>
<script>
/*
Put this part on the client:


javascript: if (document.URL.match('.*netgear.rohidekar.com.*')) { console.debug('accidental double click'); } else { window.location.href = 'http://netgear.rohidekar.com/yurl/stash2.html?url=' + encodeURIComponent(document.URL) + '&nodeId=45'  ;}

main: 45, product: 29196, video: 37658, tech: 46, other: 29172 

*/
	function sendUrl() {

		function getParameterByName(name) {
			name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
			var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
				results = regex.exec(location.search);
			return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
		}

		if (location.href.match("youtu.*&list=.*") ) {
			var newLocation=location.href.replace(/&list=[^&]+/,"");
			newLocation = newLocation.replace(/.*watch/,"/watch");
			window.history.pushState("object or string", "Title", newLocation);
		}
		var url = getParameterByName('url');
		/* it seems we can't close the tab before executing the rest of the code */
		document.getElementsByTagName("body")[0].innerHTML = "Saving...<br><a href='" + url + "'>" + url + "</a>";
		document.body.style.backgroundColor = "#FFCC66";
		var x = new XMLHttpRequest();
		/* main: 45, product: 29196, video: 37658, tech: 46, other: 29172 */
		x.open('GET','http://netgear.rohidekar.com:4447/yurl/stash?rootId='
			+ encodeURIComponent(getParameterByName('nodeId')) 
			+ '&param1='
			+  encodeURIComponent(getParameterByName('url')),true);
		x.onreadystatechange = 
			function() {
				if (x.readyState == 4) {
					if (x.status == 200) {
						/* window.open('', '_self', '');  */
						/* window.close(); */
						document.body.style.backgroundColor = "#99CC33";
						document.getElementsByTagName("body")[0].innerHTML = "Success:<br><a href='" + url + "'>" + url + "</a>";
						document.title = "(stashed)";
						(function() {
							var link = document.createElement('link');
							link.type = 'image/x-icon';
							link.rel = 'shortcut icon';
							link.href = 'http://netgear.rohidekar.com/static/icons/tick.ico';
							document.getElementsByTagName('head')[0].appendChild(link);
						}());
					} else {
						document.body.style.backgroundColor = "#CC0033";
						document.getElementsByTagName("body")[0].innerHTML = "Error:<br><a href='" +  url + "'>" + url + "</a>";
						// TODO: show the existing title
						(function() {
							var link = document.createElement('link');
							link.type = 'image/x-icon';
							link.rel = 'shortcut icon';
							link.href = 'http://www.favicon.cc/favicon/392/638/favicon.png';
							document.getElementsByTagName('head')[0].appendChild(link);
						}());
						alert(x.status);
					}
				}
			};
		x.send();
//		window.history.pushState();
	}
</script>
</head>
<body onload="sendUrl()" >
</body>
</html>
