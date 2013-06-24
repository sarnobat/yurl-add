import org.htmlcleaner.*;


HttpURLConnection con = (HttpURLConnection) new URL("http://www.yahoo.com").openConnection();
con.connect();

InputStream inputStream = con.getInputStream();

HtmlCleaner cleaner = new HtmlCleaner();
CleanerProperties props = cleaner.getProperties();

TagNode node = cleaner.clean(inputStream)
TagNode titleNode = node.findElementByName("title", true);

String title = titleNode.getText().toString();
println(title)