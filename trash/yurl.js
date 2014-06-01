
var urlBase = "http://netgear.rohidekar.com:4447/yurl";
var rootId;
var parentOfRootId;
var orderedDescending = true;

$(document).ready(function() {

	////
	//// Determine the root ID
	////
	{
		getRootId: {
			rootId = $.url().param('rootId');
		}
	
		updateURL: {
			if (rootId == null || rootId == '') {
				rootId = 45;
				history.pushState(null, null, '/yurl?rootId=' + rootId); // HTML5
			}
		}
		
		setCountAndTitle: {
			$.getJSON(urlBase + "/count_non_recursive?rootId=" +rootId,function(result){
				$("#count").text(result.count);
				$("#category").text(result.name);
				document.title = result.name + " (yurl)";
			});
		}
	
		setParentOfRootId(rootId);
		
		showLoading : {
			$("#status").text("Loading"); 			
		}

		setColorForCategory : {		
			var rootColor = MD5(rootId.toString()).substring(0,6);
			$("#rootColor").attr("style", "background-color: #" + rootColor);
		}
	
		var limit = $.url().param('limit');
		if (limit == null) {
			limit = 50;
			window.history.pushState("object or string", "Title", document.URL + "&limit=50");
		}
	}
	////
	//// Get the key bindings and categories (non-recursive)
	////
	var categories;
	
	setCategoriesAndConfigureKeyBindings : {
		$.getJSON(urlBase + "/keys?parentId=" + rootId, function(result){
			var originalKeyBindings = $.extend(true, {}, result);// TODO: what does this do?
			categories = result;	  
			configureKeyBindings(categories);
		});
	
		function configureKeyBindings(result) {
			$.each(result, function(a,binding,c){
				if (binding.key == 'null') {
					$("#keys").append("#");
					// Don't remove the "null" otherwise we'll assign hash to this category
				}
				$("#keys").append(binding.key + "=" + binding.name +" # node " + binding.id + "\n");
				$("#keyLinks").append("<li style='text-transform:capitalize;' id='list_"+binding.id+"'><a href='/yurl/?rootId=" + binding.id+ "'>" + " " + binding.name + " (<span id='count_"+binding.id+"'></span>)</a></li>");
				$("#count_" + binding.id).text(binding.count);
				$(window).keypress(function(event) {
					if ($("#keys").is(":focus")) {
						return;
					}
					if (binding.key == String.fromCharCode(event.which)) {
						var parentId = binding.id;
						var childId = $("#urls").children()[0].id;
						var currentRootId = rootId;
						relate(parentId, childId, currentRootId);
					}
				});			
			});
			$(window).keypress(function(event) {
				var key = String.fromCharCode(event.which);
				if (('_' == key)||('-'==key) ||('j'==key)|| ('n' == key) ) {
					var idOfTop = $("#urls").children()[0].id;
					moveToBottom(idOfTop);
				}
				if (String.fromCharCode(event.which) == '<') {
					var parentId = parentOfRootId;
					var childId = $("#urls").children()[0].id;
					var currentRootId = rootId;
					relate(parentId, childId, currentRootId);
				}
				if (String.fromCharCode(event.which) == '>') {
					console.debug("remove from list (deal with it later)");
				}
			});
		}
	}
	
	////
	//// Uncategorized URLs (main part)
	////
    
    getUrls : {
    	var absoluteUrl = urlBase + "/uncategorized?rootId=" +rootId;
		$.getJSON(absoluteUrl,function(result){
			$("#status").text("Populating");
			$.each(result, function(resultRowNumber, field) {
			
				if (resultRowNumber > limit && resultRowNumber < result.length - 10)  {
					return;
				}
				
				var one = $("<td style='background-color:'>1");
				var two = $("<td style='background-color:' >2");
				var three = $("<td style='background-color:'>3");
				var four = $("<td style='background-color:'>4");
				var five = $("<td style='background-color:'>5");
				
				var table = $("<table/>");
				{
					table.attr('width','100%');
		
					{
						var row1 = $("<tr/>");
						table.append(row1);
						row1.append(two);
						row1.append(three);
					}
		
					{
						var row2 = $("<tr/>");
						table.append(row2);
						row2.append(four);
						row2.append(five);
					}
		
					{
						var listItem = $("<li>").attr('id',field.id).attr("class",'buttonize').attr('style','background-color:#FDFD96');
						listItem.append(table);
						$("#urls").append(listItem);
					}
		
				}
				
				{
					var urlElements = field.url.match(/^http:\/\/[^/]+/);
					if (urlElements != null) {
						var site = urlElements[0];
						two.append("<img src='" + site + "/favicon.ico' width=18 onerror=\"this.style.display='none'\" > ");
					}
					two.append("<font style=\"color: #002366\">"+ field.title + "</font><br><sub><font style=\"color: #436B95\"><a href='" +field.url+"'>"+field.url+"</a></font></sub>");
				}
				
				{
					var imageCell = three;
					var hasScreenshot = false;
					var imageSize = 300;
					{
						if (field.url.match(".jpg\??") || 
							field.url.match(".gif$") || 
							field.url.match(".png\??") || 
							field.url.match("images.q=tbn:")) {
							imageCell.append("<br><img src="+field.url+" width="+imageSize+">");
							hasScreenshot = true;
						}
						if (field.url.match("youtube.com")) {
							if (field.url.match("youtube.com/watch")) {
								var youtubeId = field.url.replace(/^https?:..www.youtube.com.watch.*v=([^&]+).*/g,'$1');
								imageCell.append("<br>");
								imageCell.append("<img src='http://img.youtube.com/vi/"+youtubeId+"/0.jpg' width="+imageSize+">");
								
								imageCell.append("<br>");
								imageCell.append("");
								imageCell.append("");
								hasScreenshot = true;
							}
							
						} else if (field.url.match("dailymotion.com/video")) {
								var youtubeId = field.url.replace(/^http.*video.([^?]+)(.*)/g,'$1');
								imageCell.append("<br>");
								imageCell.append("<img src='http://dailymotion.com/thumbnail/video/"+youtubeId +"' width="+imageSize+">");
								imageCell.append("<br>");
								imageCell.append("");
								imageCell.append("");
								hasScreenshot = true;
							
						} else if (field.url.match(".amazon.co[^/]+\/[^s]")) {
							imageCell.append("<br>");
							var asin = field.url.replace(/.+dp\/([^\/]+)\/?.*/,'$1');
							var productImageUrl = 'http://images.amazon.com/images/P/'+asin+'.01.LZZZZZZZ.jpg';
							imageCell.append("<img src='"+productImageUrl+"' height='280'>");
							hasScreenshot = true;
						}
					}
					
					if (!hasScreenshot) {
						imageCell.append("<img src=\"http://free.pagepeeker.com/v2/thumbs.php?size=x&url="+encodeURIComponent(field.url)+"\" class='screenshot' width="+imageSize+" /><br>");
					}
				}
	
				{
					var buttonCell = two;
					buttonCell.append("<br>");
					buttonCell.append("<br>");
					buttonCell.append("<input type=button class='genericButton' value=Tag onClick='tagUrlWithSelections("+field.id+")'><br>");			
					buttonCell.append("<br>");
					buttonCell.append("<br>");
					buttonCell.append("<input type=button class='genericButton' value='Top' onclick='moveToTop("+field.id+")'><br>");
					buttonCell.append("<input type=button class='genericButton' value='Move Up' onclick='moveUp("+field.id+")'><br>");
					buttonCell.append("<input type=button class='genericButton' value='Move Down' onclick='moveDown("+field.id+")'><br>");
					buttonCell.append("<input type=button class='genericButton' value='Bottom' onclick='moveToBottom("+field.id+")'><br>");
					buttonCell.append("<sub>"+field.id+"</sub>");
				}
				
				{
					var categoryButtons = four;
					
					// TODO: 'categories' may not yet be set. Really we should add these buttons in the callback where "categories" is set.
					$.each(categories, function(a,binding,c){
						four.append("<input type=button class='genericButton' value='"+binding.name+"' onclick='relate("+binding.id+","+field.id+","+rootId+")'>&nbsp;");
					});
	
				}
				
				{
					five.append("<input type=button class='genericButton' value='I own this' onclick='tagUrlWithCategoryIds("+field.id+",createArrayWithId("+37373+"), this)'><br>");
					five.append("<input type=button class='genericButton' value='Buy this' onclick='tagUrlWithCategoryIds("+field.id+",createArrayWithId("+221013+"), this)'><br>");
					five.append("<input type=button class='genericButton' value='Download this' onclick='tagUrlWithCategoryIds("+field.id+",createArrayWithId("+221026+"), this)'><br>");
					five.append("<input type=button class='genericButton' value='Watched' onclick='tagUrlWithCategoryIds("+field.id+",createArrayWithId("+37652+"), this)'><br>");
					five.append("<input type=button class='genericButton' value='Watch this' onclick='tagUrlWithCategoryIds("+field.id+",createArrayWithId("+37567+"), this)'><br>");
				}	
			});
			$("#status").text("Done");
		});
	}
	    
    ////
    //// Get all recursive categories bindings
    ////
    
    // Unfortunately, there is no easy way to get a tree of all category nodes.
    // If this were an enterprise app, I'd go through the extra effort but for a 
    // toy app like this it's not worth the effort.
    var nodeIdZero = 0;
    getCategoriesTree : {
		var url = urlBase + "/categoriesRecursive?parentId=" + nodeIdZero;
		$.getJSON(url, function(result){})
			.success(
				function(data) { 
					  
					  console.debug(data.categoriesTree);
					  
					  drawVisualization(data.categoriesTree);
					  
					  writeCategoryTree(data.categoriesTree, 0);
					  $.each(data.flat, function(a,category,c){
							$("#categoriesRecursive").append("<input type=checkbox value=false name="+category.id +"><a href='/yurl/?rootId="+category.id+"' style='text-transform:capitalize;'>" + category.name + " (" + category.id + ")</a></input>");
							$("#categoriesRecursive").append("<br>");
						});
				}
			)
			.error(function() {  })
			.complete(function() {  }
		);
	}


	////
    //// Update the key bindings
    ////
    
    addKeyBindingsUpdateHandler : {
		var oldKeyBindings;
		$("#keys").focus(function() {
			oldKeyBindings = $.trim($("#keys").val());
		}).blur(function() {
			var newKeyBindings = $.trim($("#keys").val());
			var i = 1;
			var url;
			if (oldKeyBindings === newKeyBindings) {
			} else {
				url = urlBase + "/keysUpdate?parentId=" + $.url().param('rootId') + "&newKeyBindings=" + encodeURIComponent(newKeyBindings) + "&oldKeyBindings=" + encodeURIComponent(oldKeyBindings);
			}
	
			$.getJSON(url, function(result){})
				.success(function(data) {  
					configureKeyBindings(data);
				})
				.error(function() { })
				.complete(function() { });
		});
    }
    
});

function relate(parentId, childId, currentRootId) {
	var url = urlBase + "/relate?parentId=" +parentId+"&childId=" +  childId + "&currentParentId=" + currentRootId;	
	//
	// The main part
	//
	$.getJSON(url, function(result){})
		.success(function() { 
			removeTop(parentId, childId);
			$("#list_"+parentId).effect("highlight", null, 4000, function() {});
			
			// Change the destination category count
			var count = parseInt($("#count_"+parentId).text()) + 1;
			$("#count_"+parentId).text(count);
			$("#count_"+parentId).css("font-weight","Bold");
			
			// Decrement the number of items in this category that is displayed
			var newCount = parseInt($("#count").text());
			newCount -= 1;
			$("#count").text(newCount);
		})
		.error(function() { alert("error occurred "); })
		.complete(function() { });
}

// This part just performs frontend changes, nothing backend
function removeTop(parentId, childId) {
	var target;
	if (parentId == parentOfRootId) {
		target = "#up";
	} else {
		target = "#list_"+parentId;
	}
	var options = { to: target, className: "ui-effects-transfer" };
	$($("#" + childId)).effect('transfer',options,50,function() { // !!!!!!!! TODO : Wrong. remove the one specified by child ID
		$($("#" + childId)).effect( "blind",null, 10, function() { $(this).remove(); });
	});	
}
  
function writeCategoryTree(root, level) {
	var indent = "";
	for (var i = 0; i < level; i++) {
		indent = indent + "&nbsp;&nbsp;&nbsp;&nbsp;";
	}
	$("#categoriesTree").append(indent + "<a href='/yurl/?rootId="+root.id+"' style='text-transform:capitalize;'>"+root.name + "</a>");
	$("#categoriesTree").append("<br>");
	
	if (root.children != null) {
		$.each(root.children, function(a,b){
			
			writeCategoryTree(b, level + 1);
		});
	}
}

function moveToTop(source) {
	var idToMove = source;
	var firstId = $("#urls").children().first().attr("id");


	if (orderedDescending) {
		// 1 more than latest timestamp
		$.getJSON(urlBase + "/surpassOrdinal?nodeIdToChange=" + idToMove + "&nodeIdToSurpass=" + firstId,
			function(result){
			})
			.success(function(data) {  
				var elem = $("#" + idToMove).remove();
				$("#urls").prepend(elem);
			})
			.error(function() {
				alert("error occurred "); 
			})
			.complete(function() { });
	} else {
		// 1 less than earliest timestamp
		alert('not implemented');
	}
}

function moveToBottom(source) {
	var idToMove = source;
	var lastId = $("#urls").children().last().attr("id");

	if (orderedDescending) {
		$.getJSON(urlBase + "/undercutOrdinal?nodeIdToChange=" + idToMove + "&nodeIdToUndercut=" + lastId, 
			function(result){
			})
			.success(function(data) {  
				var elem = $("#" + idToMove).remove();
				$("#urls").append(elem);
			})
			.error(function() {
				alert("error occurred "); 
			})
			.complete(function() { });
	} else {
		alert('not implemented');
	}
}

function moveUp(source) {
	var first = $("#" + source).prev().attr('id');
	var second = source;
	swap(first, second);
}

function moveDown(source) {
	var first = source;
	var second = $("#" + source).next().attr('id');
	swap(first, second);
}

function swap(firstId, secondId) {
	$.getJSON(urlBase + "/swapOrdinals?firstId=" + firstId + "&secondId=" + secondId, 
		function(result){
		})
		.success(function(data) {  
			$("#" + secondId).after($("#" + firstId));
		})
		.error(function() { 
			alert("error occurred "); 
		})
		.complete(function() { 
		});

}

function sendBatch() {
	var urls = encodeURIComponent($("#urlBatchToSend").val());
	$("#batchStatus").text("Sending new batch");	
	$.getJSON(urlBase + "/batchInsert?rootId=" + rootId + "&urls=" + urls, function(result){})
		.success(function(data) {
			$("#batchStatus").text("Successfully imported batch, except for ones remaining in text box");
			$("#urlBatchToSend").val("");
			$("#urlBatchToSend").val(data.unsuccessful);
		})
		.error( function(data) {
			alert("error occurred "); 
		} )
		.complete( function() {} );
}

function createArrayWithId(id) {
	var selected = new Array();
	selected.push(id);
	return JSON.stringify(selected);
}


function getTagSelections() {
	var selected = new Array();
	$('#categoriesRecursive input:checked').each(function() {
		selected.push($(this).attr('name'));
	});
	return JSON.stringify(selected);
}

function tagUrlWithSelections(nodeId) {
	var selections = getTagSelections();
	tagUrlWithCategoryIds(nodeId, selections);
}

function tagUrlWithCategoryIds(nodeId, selections, button) {
	var url = urlBase + "/relateCategoriesToItem?nodeId=" + nodeId + "&newCategoryIds=" + encodeURIComponent(selections);
	$.getJSON(url, 
		function(result){
		})
		.success(function(data) {  
			//$("#"+nodeId).remove();
			button.style.cssText = "background-color : green";
		})
		.error(function() { 
			alert("error occurred "); 
		})
		.complete(function() { 
		}
	);
}

function setParentOfRootId(nodeId) {    	
	$.getJSON(urlBase + "/parent?nodeId=" + nodeId, function(result){
	})
	.success(function(data) {  
		if (data[0] != null) {
			parentOfRootId = data[0].id;
			$("#up").attr("href", "/yurl/?rootId=" + parentOfRootId);
		}
	})
	.error(function() { })
	.complete(function() { });
}

function drawVisualization(data) {
	var ua = navigator.userAgent;
	var forceDirectedGraph = new $jit.ForceDirected({
		injectInto: 'infovis',
		Node: {
			overridable: true
		},
		Edge: {
			overridable: true
		},
		Label: {  
			type: 'Native', //Native or HTML  
			size: 5,  
			style: 'bold' 
		  },
		Navigation: {  
			enable: true,  
			//Enable panning events only if we're dragging the empty  
			//canvas (and not a node).  
			panning: 'avoid nodes',  
			zooming: 10 //zoom speed. higher is more sensible  
		},
	});

	var listOfNodes;
  
	{
		var adjacenciesList = [];
		for (var i = 0; i < data.children.length; i++) {
			adjacenciesList[i] = { "nodeTo" : data.children[i].id };
		}
		
		
		lon = [{
		
			id:45,
			data : {
				"$color"		:	"#C74243",
				"$type"			:	"star",
				"$dim"			: 	17
			},
			adjacencies : adjacenciesList
		}];
		
		var listOfNodes = lon;
		addNodes(data, listOfNodes, 0);
		
		
	}

	console.debug("List of nodes...");
	console.debug(listOfNodes);
	
	forceDirectedGraph.loadJSON(listOfNodes); 

	forceDirectedGraph.computeIncremental({
		onComplete: function(){
			forceDirectedGraph.animate({
			});
		}
	});
	 
}

function addNodes(data, listOfNodes, level1) {
	if (data.children == null) {
		return;
	}
	++level1;
	for (var i = 0; i < data.children.length; i++) {
		var category = data.children[i];
		listOfNodes.push({ 
			id : category.id,
			name : category.name,
			"level" : level1,
			data : {
				"$color"		:	getColorForLevel(level1),
				"$type"			:	getShapeForLevel(level1),
				"$dim"			: 	4
			},
			adjacencies : getAdjacencies(category),
		});
		
		addNodes(category, listOfNodes, level1);
	}
}


function getColorForLevel(level) {
	if (level == 1) {
		return "red";
	}
	if (level == 2) {
		return "green";
	}
	if (level == 3) {
		return "blue";
	}
	return "yellow";
	
}

function getShapeForLevel(level) {
	if (level == 1) {
		return "star";
	}
	if (level == 2) {
		return "square";
	}
	if (level == 3) {
		return "triangle";
	}
	return "circle";
	
}

function getAdjacencies(category) {
	var adjacenciesList = [];
	
	if (category.children !=null) {
		for (var i = 0; i < category.children.length; i++) {
			adjacenciesList[i] = { "nodeTo" : category.children[i].id };
		}
	}
	return adjacenciesList;
}

// TODO: Remove. Unused
function addEmbeddedVideo(id, url) {
	$("#" + id).children().last().append("<iframe width=420 height=345 src="+url.replace("watch?v=","embed/")+"></iframe>");
}

