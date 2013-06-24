import org.htmlcleaner.*;


String getTitle(String url) {
	HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
	con.connect();
	
	InputStream inputStream = con.getInputStream();
	
	HtmlCleaner cleaner = new HtmlCleaner();
	CleanerProperties props = cleaner.getProperties();
	
	TagNode node = cleaner.clean(inputStream)
	TagNode titleNode = node.findElementByName("title", true);
	
	String title = titleNode.getText().toString();

	return title;
}
println(getTitle("http://www.yahoo.com"));