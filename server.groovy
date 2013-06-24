import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.apache.commons.lang.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class Server {
	@Path("yurl")
	public static class HelloWorldResource { // Must be public

		final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";

		@GET
		@Path("parent")
		@Produces("application/json")
		public Response parent(@QueryParam("nodeId") Integer nodeId) throws JSONException,
				IOException {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("nodeId", nodeId);
			// TODO: order these by most recent-first (so that they appear this
			// way in the UI)
			JSONObject json = execute(
					"start n=node({nodeId}) MATCH p-[r:CONTAINS]->n RETURN id(p)", params);
			JSONArray data = (JSONArray) json.get("data");
			JSONArray ret = new JSONArray();
			for (int i = 0; i < data.length(); i++) {
				JSONArray a = data.getJSONArray(i);
				JSONObject o = new JSONObject();
				String id = (String) a.get(0);
				o.put("id", id);
				ret.put(o);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(ret.toString())
					.type("application/json").build();
		}

		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response uncategorized(@QueryParam("rootId") Integer rootId) throws JSONException,
				IOException {
			// TODO: check rootId is not null or empty
			checkNotNull(rootId);
			// TODO: the source is null clause should be obsoleted
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("rootId", rootId);
			// TODO: order these by most recent-first (so that they appear this
			// way in the UI)
			JSONObject json = execute(
					"start n=node(*) MATCH n<-[r?:CONTAINS]-source where (source is null or ID(source) = {rootId}) and not(has(n.type)) AND id(n) > 0 return distinct ID(n),n.title?,n.url?",
					params);
			JSONArray data = (JSONArray) json.get("data");
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
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(ret.toString())
					.type("application/json").build();
		}

		@GET
		@Path("keysUpdate")
		@Produces("application/json")
		// Ideally we should wrap this method inside a transaction but over REST I don't know how to do that.
		// TODO: we will have to start supporting disassociation of key bindings
		// with child categories
		public Response keysUpdate(@QueryParam("parentId") Integer parentId,
				@QueryParam("newKeyBindings") String newKeyBindings,
				@QueryParam("oldKeyBindings") String oldKeyBindings) throws JSONException,
				IOException {
			System.out.println("keysUpdate");

			Set<String> oldKeyBindingsSet = new HashSet<String>();
			Collections.addAll(oldKeyBindingsSet, oldKeyBindings.trim().split("\n"));
			Set<String> newKeyBindingsSet = new HashSet<String>();
			Collections.addAll(newKeyBindingsSet, newKeyBindings.trim().split("\n"));

			// NOTE: This is not symmetric (commutative?). If you want to
			// support removal do that in a separate loop
			Set<String> newKeyBindingLines = Sets.difference(newKeyBindingsSet, oldKeyBindingsSet);
			System.out.println("Old: " + oldKeyBindingsSet);
			System.out.println("New: " + newKeyBindingsSet);
			System.out.println("Difference: " + newKeyBindingLines);
			Map<String, String> keyBindings = new HashMap<String, String>();
			for (String newKeyBinding : newKeyBindingLines) {
				if (newKeyBinding.trim().startsWith("#") && !newKeyBinding.trim().startsWith("#=")) {
					continue;// A commented out keybinding
				}
				String[] lineElements = newKeyBinding.split("=");
				String aKeyCode = lineElements[0].trim();

				// Ignore trailing comments
				String[] aRightHandSideElements = lineElements[1].trim().split("#");
				String aName = aRightHandSideElements[0].trim();

				//
				System.out.println("Accepting proposal to create key binding for " + aName + "("
						+ aKeyCode + ")");
				keyBindings.put(aKeyCode, aName);

				// TODO: if it fails, recover and create the remaining ones?
			}

			for (String aKeyCode : keyBindings.keySet()) {
				String aName = keyBindings.get(aKeyCode);
				Map<String, Object> paramValues = new HashMap<String, Object>();
				paramValues.put("parentId", parentId);
				paramValues.put("key", aKeyCode);
				System.out.println("About to remove keybinding for " + aKeyCode);
				JSONObject json = execute(
						"START parent=node( {parentId} ) MATCH parent-[r:CONTAINS]->category WHERE has(category.key) and category.type = 'categoryNode' and category.key = {key} DELETE category.key RETURN category",
						paramValues);
				keyBindings.remove(aKeyCode);
				System.out.println("Removed keybinding for " + aName);

				createNewKeyBinding(aName, aKeyCode, parentId);
			}
			JSONArray ret = getKeys(parentId);
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(ret.toString())
					.type("application/json").build();
		}

		private void createNewKeyBinding(String aName, String aKeyCode, Integer parentId)
				throws IOException, JSONException {
			// Create a new node
			// TODO: Check if the category already exists
			Map<String, Object> paramValues = new HashMap<String, Object>();
			paramValues.put("name", aName);
			paramValues.put("key", aKeyCode);
			paramValues.put("type", "categoryNode");
			paramValues.put("created", System.currentTimeMillis());
			System.out.println("cypher params: " + paramValues);

			// TODO: first check if there is already a node with this name,
			// which is for re-associating the keycode with the category
			JSONObject json = execute(
					"CREATE (n { name : {name} , key : {key}, created: {created} , type :{type}}) RETURN id(n)",
					paramValues);
			System.out.println(json.toString());
			Integer newCategoryNodeId = Integer.parseInt((String) ((JSONArray) ((JSONArray) json
					.get("data")).get(0)).get(0));

			relateHelper(parentId, newCategoryNodeId);
		}

		@GET
		@Path("keys")
		@Produces("application/json")
		public Response keys(@QueryParam("parentId") Integer parentId) throws JSONException,
				IOException {
			JSONArray ret = getKeys(parentId);
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(ret.toString())
					.type("application/json").build();
		}

		public JSONArray getKeys(Integer parentId) throws IOException, JSONException {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("parentId", parentId);
			JSONObject json = execute(
					"START n=node(*) MATCH parent-[c:CONTAINS]->n WHERE has(n.name) and n.type = 'categoryNode' and id(parent) = {parentId} RETURN ID(n),n.name,n.key?",
					params);
			JSONArray data = (JSONArray) json.get("data");
			JSONArray ret = new JSONArray();
			for (int i = 0; i < data.length(); i++) {
				JSONArray a = data.getJSONArray(i);
				JSONObject o = new JSONObject();
				String id = (String) a.get(0);
				o.put("id", id);
				String title = (String) a.get(1);
				String url = (String) a.get(2);
				o.put("name", title);
				o.put("key", url);// TODO: this could be null
				ret.put(o);
			}
			return ret;
		}

		@GET
		@Path("stash")
		@Produces("application/json")
		public Response stash(@QueryParam("param1") String url) throws JSONException, IOException {
			String httpUrl = URLDecoder.decode(url, "UTF-8");
			String title = getTitle(new URL(httpUrl));
			Map<String, Object> paramValues = new HashMap<String, Object>();
			paramValues.put("url", httpUrl);
			paramValues.put("title", title);
			paramValues.put("created", System.currentTimeMillis());
			JSONObject json = execute(
					"CREATE (n { title : {title} , url : {url}, created: {created} }) RETURN id(n)",
					paramValues);

			JSONArray newNodeId = (JSONArray) ((JSONArray) json.get("data")).get(0);
			System.out.println("New node: " + newNodeId.get(0));
			// TODO: Do not hard-code the root ID
			JSONObject json2 = relateHelper(new Integer(45), (Integer) newNodeId.get(0));
			// TODO: check that it returned successfully (redundant?)
			System.out.println(json2.toString());
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
		public Response relate(@QueryParam("parentId") Integer newParentId,
				@QueryParam("childId") Integer childId,
				@QueryParam("currentParentId") Integer currentParentId) throws JSONException,
				IOException {
			// TODO: first delete any existing contains relationship with the
			// specified existing parent (but not with all parents since we
			// could have a
			// many-to-one contains)
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("currentParentId", currentParentId);
			params.put("childId", childId);

			execute("START oldParent = node({currentParentId}), child = node({childId}) MATCH oldParent-[r:CONTAINS]-child DELETE r",
					params);

			Map<String, Object> paramValues = new HashMap<String, Object>();
			paramValues.put("childId", childId);

			JSONObject json = relateHelper(newParentId, childId);
			JSONObject ret = new JSONObject();
			ret.put("status", "FAILURE");
			if (((JSONArray) json.get("data")).length() == 0) {
				if (((JSONArray) json.get("columns")).length() == 0) {
					ret.put("status", "SUCCESS");
				}
			}

			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(ret.toString())
					.type("application/json").build();
		}

		/**
		 * @throws RuntimeException
		 *             - If the command fails. This could legitimately happen if
		 *             we try to relate to a newly created category if the
		 *             system becomes non-deterministic.
		 */
		private JSONObject relateHelper(Integer parentId, Integer childId) throws IOException,
				JSONException {
			Map<String, Object> paramValues = new HashMap<String, Object>();
			paramValues.put("parentId", parentId);
			paramValues.put("childId", childId);
			JSONObject json = execute(
					"start a=node({parentId}),b=node({childId}) create a-[r:CONTAINS]->b return a,r,b;",
					paramValues);
			return json;
		}

		private JSONObject execute(String cypherQuery, Map<String, Object> params)
				throws IOException, JSONException {
			WebResource resource = Client.create().resource(CYPHER_URI);
			Map<String, Object> postBody = new HashMap<String, Object>();
			postBody.put("query", cypherQuery);
			postBody.put("params", params);
			// POST {} to the node entry point URI
			ClientResponse response = resource.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).entity("{ }")
					.post(ClientResponse.class, postBody);
			if (response.getStatus() != 200) {
				System.out.println("failed: " + cypherQuery + "\tparams: " + params);
				throw new RuntimeException();
			}
			String neo4jResponse = IOUtils.toString(response.getEntityInputStream());
			System.out.println(neo4jResponse);
			response.getEntityInputStream().close();
			response.close();
			JSONObject json = new JSONObject(neo4jResponse);
			return json;
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		JdkHttpServerFactory.createHttpServer(new URI("http://localhost:4447/"),
				new ResourceConfig(HelloWorldResource.class));
	}
}
