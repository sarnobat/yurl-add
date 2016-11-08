var windowHeight =  $(window).height() ;
var windowWidth = $(window).width();
$(document).mousemove(function(e) {
    	var mouseX = e.pageX;
    	var mouseY = e.pageY;
//	console.debug('mouse: ' + mouseX + ", " + mouseY);
	var direction = 'left';
	if (mouseX > windowWidth/2) {
		direction = 'right';
	} 
	$('#enlarged_image').css('top', mouseY + 10 - window.pageYOffset).css(direction, mouseX + 10);
}).mouseover();

function zoom(img) {
	var url = img.src;
	$("body").append("<div id='enlarged_image' style='position: fixed; top: 30px; left: 5px;'></div>")
	var overlay = document.getElementById('enlarged_image');
	var imgClone =  img.cloneNode();
	img.onmouseleave = function() {
		var overlay = document.getElementById('enlarged_image');
		overlay.innerHTML = "";
	}
	imgClone.onmouseenter = null;
	imgClone.onmouseleave = null;
	$(imgClone).css('height', $(window).height() * 2/3).css('width','auto');
	overlay.appendChild(imgClone);
	overlay.innerHTML += "<br><textarea cols=40>" + url +"</textarea>";
}

