var $ = require('cheerio')
var request = require('request')
var url=require('url');
var sleep = require('sleep');

var targetUrl = 'http://www.teamtalk.com/liverpool'
var domain =  url.parse(targetUrl).hostname;

var allImages = {};
var allImagesArray = [];
    
function gotHTML(err, resp, html) {
	allImages = {};
	allImagesArray = [];
  if (err) return console.error(err)
	console.log('goHTML');
  // get all img tags and loop over them
  var imageURLs = []
  	var parsedHTML = $.load(html)

  var b = parsedHTML('img').map(function(i, link) {
    var href = $(link).attr('src');
    if (href.indexOf('/') === 0) {
		imageURLs.push(domain + href);
	} else {
		imageURLs.push(href);
	}


	//console.log(imageURLs[0]);
  });
  
  //console.log(imageURLs);


  for (var i = 0; i < imageURLs.length; i++) {
  	var imgUrl = imageURLs[i];
	//console.log("<img src='" + imageURLs[i] + "'/>");	

	
	(function(theUrl, idx) {
		request(theUrl, function (err, res, body){
			if (res == null) {
				console.log("null response from: " + theUrl);
				return;
			}
			
			
			
			//console.log("::::::::::" + JSON.stringify(res));
			var entry =  {
			 "length" : parseInt(res.headers['content-length']),
			 "url" : theUrl,
			};
	
			allImages[theUrl] = entry;
			allImagesArray.push(entry);
	
		}); 		
 	})(imgUrl,i);
  }


}

function compare(a,b) {
  if (a.length < b.length)
     return 1;
  if (a.length > b.length)
    return -1;
  return 0;
}


request(targetUrl, gotHTML)


setTimeout(function() {
	var allImagesArraySorted = allImagesArray.sort(compare);
	console.log(JSON.stringify(allImagesArraySorted));
}, 5000);

