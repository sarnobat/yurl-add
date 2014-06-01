var $ = require('cheerio')
var request = require('request')

function gotHTML(err, resp, html) {

  if (err) return console.error(err)
	console.log('goHTML');
  // get all img tags and loop over them
  var imageURLs = []
  	var parsedHTML = $.load(html)

  var b = parsedHTML('img').map(function(i, link) {
    var href = $(link).attr('src');
	imageURLs.push(domain + href);
	imageURLs.push(href);
	//console.log(imageURLs[0]);
  });
  
  //console.log(imageURLs);
  for (var i = 0; i < imageURLs.length; i++) {
 	console.log("<img src='" + imageURLs[i] + "'/>");
  }
  
  // TODO: Sort the images by size from the http content-length header
}

var domain = 'http://www.teamtalk.com/'
request(domain, gotHTML)