import org.htmlcleaner.*;
import org.codehaus.jettison.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;

String getTitle(String url) {
	HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
	con.connect();
	
	InputStream inputStream = con.getInputStream();
	
	HtmlCleaner cleaner = new HtmlCleaner();
	CleanerProperties props = cleaner.getProperties();
	
	TagNode node = cleaner.clean(inputStream)
	TagNode titleNode = node.findElementByName("title", true);
	
	String title = titleNode.getText().toString();

	//return JSONObject.quote("\"We are the best\"");
	return StringEscapeUtils.escapeJava(title);
}
println(getTitle("http://www.yahoo.com"));