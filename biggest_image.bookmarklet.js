javascript:
function parseUri(str) {
    var o = parseUri.options,
        m = o.parser[o.strictMode ? "strict" : "loose"].exec(str),
        uri = {},
        i = 14;
    while (i--) uri[o.key[i]] = m[i] || "";
    uri[o.q.name] = {};
    uri[o.key[12]].replace(o.q.parser, function($0, $1, $2) {
        if ($1) uri[o.q.name][$1] = $2
    });
    return uri
};
parseUri.options = {
    strictMode: false,
    key: ["source", "protocol", "authority", "userInfo", "user", "password", "host", "port", "relative", "path", "directory", "file", "query", "anchor"],
    q: {
        name: "queryKey",
        parser: /(?:^|&)([^&=]*)=?([^&]*)/g
    },
    parser: {
        strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
        loose: /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
    }
};
console.debug("start");
var parsedUrl = parseUri(document.URL),
    base_url = parsedUrl.protocol + "://" + parsedUrl.host + "/" + parsedUrl.directory,
    imgs = document.body.getElementsByTagName("img"),
    last_winner = "",
    last_winner_pixels = 0;
    
console.debug("iterating through images: ");
console.debug(imgs);
for (var i = 0; i < imgs.length; i++) {
console.debug("i = " + i);
    var el = imgs[i],
        h = el.offsetHeight,
        w = el.offsetWidth,
        pixels = w * h;
    if (pixels > last_winner_pixels) {
        last_winner_pixels = pixels;
        last_winner = imgs[i].getAttribute("src");
	 	console.debug("1 last_winner = " + last_winner);
    }
console.debug(last_winner.substring(0, 2));
    if (last_winner.substring(0, 2) === "//") {
     last_winner = window.location.protocol + last_winner;
     console.debug("2 last_winner = " + last_winner);
    } else if (last_winner.substring(0, 4) != "http") {
     last_winner = base_url + last_winner;
     console.debug("3 last_winner = " + last_winner);
    } else {
     console.debug("4 last_winner = " + last_winner);
    }
}
window.location = last_winner;
/* by @tiagopedras */
