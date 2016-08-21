var request = require('request')
<<<<<<< HEAD
var $ = require('cheerio')
=======
>>>>>>> 85aced643fcc9b317f01039680722ec6cae072bd
var url=require('url');
var sleep = require('sleep');
expect = require('expect.js')
var neo4j = require('neo4j')
<<<<<<< HEAD

	var biggestImages = {};
var targetUrl = 'http://www.teamtalk.com/liverpool'


var express = require('express');

var app = express();

getBiggestImage("http://lifehacker.com/5330687/items-you-can-get-great-deals-on-in-a-recession");

=======

	var biggestImages = {};
var targetUrl = 'http://www.teamtalk.com/liverpool'


var express = require('express');

var app = express();

app.all('/*', function(req, res, next) {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "X-Requested-With");
  next();
});


getBiggestImage("http://lifehacker.com/5330687/items-you-can-get-great-deals-on-in-a-recession");

>>>>>>> 85aced643fcc9b317f01039680722ec6cae072bd
app.get('/setUrls', function(req, res) {
	//getBiggestImage(targetUrl)
    res.send({"status" : "success" });
});

app.get('/biggestImages', function(req, res) {
    res.send(biggestImages);
});


function getBiggestImage(urlToFindBiggestImage) {


	var domain =  url.parse(urlToFindBiggestImage).hostname;
	
	var allImages = {}; // We're not using this
	var allImagesArray = [];
		
	function gotHTML(err, resp, html) {
		allImages = {};
		allImagesArray = [];
		if (err) {
			return console.error(err) ;
		}
		
		var imageURLs = []
		var parsedHTML = $.load(html)
	
		var b = parsedHTML('img').map(function(i, link) {
		var href = $(link).attr('src');
		if (href == null) {
			// TODO: not sure why this doesn't find images, I can see them in the HTML
			console.log("Can't find image: " + urlToFindBiggestImage);
			return;
		}
		if (href.indexOf('/') === 0) {
			imageURLs.push(domain + href);
		} else {
			imageURLs.push(href);
		}
	});
	
	for (var i = 0; i < imageURLs.length; i++) {
		var imgUrl = imageURLs[i];
		
		(function(theUrl, idx) {
			request(theUrl, function (err, res, body){
				if (res == null) {
					console.log("null response from: " + theUrl);
					return;
				}
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
	
	request(urlToFindBiggestImage, gotHTML)
	
	setTimeout(function() {
		if (allImagesArray < 1) {
			return;
		}
		var allImagesArraySorted = allImagesArray.sort(compare);
		console.log(JSON.stringify(allImagesArraySorted));
		biggestImages[urlToFindBiggestImage] = allImagesArraySorted[0].url;
	}, 5000);
		
}

var db = new neo4j.GraphDatabase('http://netgear.rohidekar.com:7474');

results = db.query ('start n=node(28974) match n-->c where has(c.title) return c.url as url,c.title as title ', function(err, result) {
        if(err) throw err;
        for (var j = 0; j < result.length; j++) {
        	if (result[j] == null) {
        		continue;
        	}
			getBiggestImage(result[j].url); // delivers an array of query results
		}
});

app.listen(3000);
console.log('Listening on port http://localhost:4452');
