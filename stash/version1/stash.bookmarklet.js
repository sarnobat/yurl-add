javascript:(
	// DO NOT USE THIS ANYMORE. Use version stash2.html
	function() {
		if (location.href.match("youtu.*&list=.*") ) {
			var newLocation=location.href.replace(/&list=[^&]+/,"");
			newLocation = newLocation.replace(/.*watch/,"/watch");
			window.history.pushState("object or string", "Title", newLocation);
		}
		/* it seems we can't close the tab before executing the rest of the code */
		document.getElementsByTagName("body")[0].innerHTML = "Saving...";
		document.body.style.backgroundColor = "#FFCC66";
		var x = new XMLHttpRequest();
		/* main: 45, product: 29196, video: 37658, tech: 46, other: 29172 */
		x.open('GET','http://netgear.rohidekar.com:4447/yurl/stash?rootId=29172&param1='
			+  encodeURIComponent(location.href),true);
		x.onreadystatechange = 
			function() {
				if (x.readyState == 4) {
					if (x.status == 200) {
						/* window.open('', '_self', '');  */
						/* window.close(); */
						document.body.style.backgroundColor = "#99CC33";
						document.getElementsByTagName("body")[0].innerHTML = "Success";
					} else {
						document.body.style.backgroundColor = "#CC0033";
						document.getElementsByTagName("body")[0].innerHTML = "Error";
						alert(x.status);
					}
				}
			};
		x.send();
	}
)()
