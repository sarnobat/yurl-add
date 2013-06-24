import java.net.URI;
import java.net.URISyntaxException;
import org.htmlcleaner.*;
import org.codehaus.jettison.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import java.net.URLDecoder;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.apache.commons.io.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


 
class MyHandler implements HttpHandler {
	public Map<String, String> getQueryMap(String query)  
	{  
		Pattern pattern = Pattern.compile("/\\?.*");
		
		Matcher matcher = pattern.matcher(query);
		String[] params = query.split("&");  
		Map<String, String> map = new HashMap<String, String>();  
		for (String param : params)  
		{  
			String name = param.split("=")[0];  
			String value = URLDecoder.decode(param.split("=")[1], "UTF-8");
			map.put(name, value);  
		}
		println(map.keySet());
		return map;  
	}  

	public void handle(HttpExchange t) throws IOException {

		JSONObject json = new JSONObject();
		String query = t.getRequestURI();
		Map<String, String> map = getQueryMap(query);  
		String  value = map.get("/?param1");
		json.put("myKey",value);
		println('Request headers: ' + t.getRequestHeaders());
		String queryString = t.getRequestURI();
		println('Request URI: ' + queryString);
		println('value: ' + value);
		
		final String SERVER_ROOT_URI = "http://localhost:7474/db/data/";
		final String nodeEntryPointUri = SERVER_ROOT_URI + "node";
		// http://localhost:7474/db/data/node
		 
		WebResource resource = Client.create().resource( nodeEntryPointUri );
		String nodeData = "{\"name\" : \"" +value+ "\",\"title\" : \""+getTitle(value)+"\"}";
		println("nodeData:" + nodeData);
		
		// POST {} to the node entry point URI
		ClientResponse response = resource.accept( MediaType.APPLICATION_JSON )
				.type( MediaType.APPLICATION_JSON )
				.entity( "{ }" )
				.post( ClientResponse.class, nodeData );
		 
		final URI location = response.getLocation();
		System.out.println( String.format(
				"POST to [%s], status code [%d], location header [%s]",
				nodeEntryPointUri, response.getStatus(), location.toString() ) );
		response.close();
	
		t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
		t.sendResponseHeaders(200, json.toString().length());
		OutputStream os = t.getResponseBody();
		os.write(json.toString().getBytes());
		os.close();
	}

	private String getTitle(String url) {
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
}
    
HttpServer server = HttpServer.create(new InetSocketAddress(4444), 0);
server.createContext("/", new MyHandler());
server.setExecutor(null); // creates a default executor
server.start();