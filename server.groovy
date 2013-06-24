import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("yurl")
	public static class HelloWorldResource { // Must be public

		final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	
		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response uncategorized(@QueryParam("rootId") String rootId) throws JSONException, IOException {
			// TODO: check rootId is not null or empty
			// TODO: the source is null clause should be obsoleted
			JSONObject json = queryNeo4j("start n=node(*) MATCH n<-[r?:CONTAINS]-source where (source is null or ID(source) = 45) and not(has(n.type)) AND id(n) > 0 return ID(n),n.title?,n.url?", new HashMap());
			JSONArray data = (JSONArray)json.get("data");
			JSONArray ret = new JSONArray();
			for (int i = 0; i < data.length(); i++) {
				JSONArray a = data.getJSONArray(i);
				JSONObject o = new JSONObject();
				String id = (String) a.get(0);
				o.put("id", id);
				String title = (String) a.get(1);
				String url = (String) a.get(2);
				o.put("title", title);
				o.put("url", url);
				ret.put(o);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}

		@GET
		@Path("keys")
		@Produces("application/json")
		public Response keys() throws JSONException, IOException {
			JSONObject json = queryNeo4j("START n=node(*) WHERE has(n.name) and has(n.key) RETURN ID(n),n.name,n.key", new HashMap());JSONArray data = (JSONArray)json.get("data");
			JSONArray ret = new JSONArray();
			for (int i = 0; i < data.length(); i++) {
				JSONArray a = data.getJSONArray(i);
				JSONObject o = new JSONObject();
				String id = (String) a.get(0);
				o.put("id", id);
				String title = (String) a.get(1);
				String url = (String) a.get(2);
				o.put("name", title);
				o.put("key", url);
				ret.put(o);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}
		
		@GET
		@Path("stash")
		@Produces("application/json")
		public Response stash(@QueryParam("param1") String url) throws JSONException, IOException {
			String httpUrl = URLDecoder.decode(url);
			String title = getTitle(new URL(httpUrl));
			Map paramValues = new HashMap();
			paramValues.put("url", httpUrl);
			paramValues.put("title", title);
			paramValues.put("created", System.currentTimeMillis());
			JSONObject json = queryNeo4j("CREATE (n { title : {title} , url : {url}, created: {created} }) RETURN id(n)", paramValues);
			
			JSONArray newNodeId = (JSONArray)((JSONArray)json.get("data")).get(0);
			println "New node: " + newNodeId.get(0);
			// TODO: Do not hard-code the root ID
			JSONObject json2 = relateHelper(new Integer(45), newNodeId.get(0));
			// TODO: check that it returned successfully (redundant?)
			println( json2.toString());
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(json2.get("data").toString()).type("application/json").build();
		}
		
		
		private String getTitle(final URL url) {
			String title = "";
			ExecutorService service = Executors.newFixedThreadPool(2);
			Collection<Callable<String>> tasks = new ArrayList<Callable<String>>();
			Callable<String> callable = new Callable<String>() {
	
				@Override
				public String call() throws Exception {
					Document doc = Jsoup.connect(url.toString()).get();
					return doc.title();
	
				}
			};
			tasks.add(callable);
			try {
				List<Future<String>> taskFutures = service
						.invokeAll(tasks, 3000L, TimeUnit.SECONDS);
				title = taskFutures.get(0).get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			return title;
		}

		@GET
		@Path("relate")
		@Produces("application/json")
		public Response relate(@QueryParam("parentId") Integer parentId, @QueryParam("childId") Integer childId) throws JSONException, IOException {
			println(1);
			// TODO: first delete any existing contains relationship with the root (but not with existing categories since we could have a many-to-one contains)
			Map paramValues = new HashMap();
						println(2);
			paramValues.put("childId", childId);
						println(3);
			JSONObject json2 = queryNeo4j("start root=node(*) match root-[c:CONTAINS]->n where has(root.type) and root.type = 'categoryNode' and root.name = 'root' and id(n) = {childId} DELETE c RETURN id(c)", paramValues);
						println(4);
			JSONArray dataArray = (JSONArray)json2.get("data");

						println(5 + "dataArray: " + dataArray.toString());
			for (int i =0; i < dataArray.length(); i++) {
				JSONArray oldRelationshipsIds = (JSONArray) dataArray.get(0);
				println("Deleted relationship: " + oldRelationshipsIds.get(0));
										println(6);
			}
			println(6);
			JSONObject json = relateHelper(parentId, childId);
			println json;
			JSONObject ret = new JSONObject();
			ret.put("status", "FAILURE");
			if (((JSONArray)json.get("data")).length() == 0) {
				if (((JSONArray)json.get("columns")).length() == 0) {
					ret.put("status", "SUCCESS");
				}
			}
			

			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}
		
		private JSONObject relateHelper(Integer parentId, Integer childId) throws IOException, JSONException {
			println "AA";
			Map paramValues = new HashMap();
			println "BB";
			paramValues.put("parentId", parentId);
			paramValues.put("childId", childId);
			println "CC";
			JSONObject json = queryNeo4j("start a=node({parentId}),b=node({childId}) create a-[r:CONTAINS]->b return a,r,b;", paramValues);
			println "DD";
			return json;
		}
		

		private JSONObject queryNeo4j(String cypherQuery, Map params) throws IOException, JSONException {
			WebResource resource = Client.create().resource(CYPHER_URI);
			println("A");
			Map map = new HashMap();
			map.put("query", cypherQuery);
			map.put("params", params);
						println("B");
			// POST {} to the node entry point URI
			ClientResponse response = resource.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).entity("{ }").post(ClientResponse.class, map);
								println("C");
			if (response.getStatus() != 200) {
						System.out.println("failed: " + cypherQuery + "\tparams: " + params);
				throw new RuntimeException();
			}
						println("E");
			String neo4jResponse = IOUtils.toString(response.getEntityInputStream());
			System.out.println(neo4jResponse);
			response.getEntityInputStream().close();
			response.close();
			JSONObject json = new JSONObject(neo4jResponse);
			return json;
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4447/"), new ResourceConfig(HelloWorldResource.class));
	}
}
