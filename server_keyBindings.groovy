import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

// TODO: Use javax.json.* for immutability
public class YurlKeyBindings {

	// Gets stored here: http://192.168.1.2:28017/cache/items/
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	
	@Path("yurl")
	// TODO: Rename to YurlResource
	public static class YurlResource { // Must be public


		static {
			// We can't put this in the constructor because multiple instances will get created
			// YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		}

		// This only gets invoked when it receives the first request
		// Multiple instances get created
		YurlResource() {
			// We can't put the auto downloader in main()
			// then either it will be called every time the cron job is executed,
			// or not until the server terminates unexceptionally (which never happens).
		}
		
		// -----------------------------------------------------------------------------
		// Key bindings
		// -----------------------------------------------------------------------------

		@GET
		@Path("keysUpdate")
		@Produces("application/json")
		// Ideally we should wrap this method inside a transaction but over REST
		// I don't know how to do that.
		// TODO: we will have to start supporting disassociation of key bindings
		// with child categories
		public Response keysUpdate(@QueryParam("parentId") Integer iParentId,
				@QueryParam("newKeyBindings") String iNewKeyBindings,
				@QueryParam("oldKeyBindings") String iOldKeyBindings)
				throws JSONException, IOException {
			System.out.println("keysUpdate() - begin");
			// Remove duplicates by putting the bindings in a map
			for (Map.Entry<String, String> pair : FluentIterable
					.from(difference(iNewKeyBindings, iOldKeyBindings))
					.filter(Predicates.not(IS_COMMENTED))
					.transform(LINE_TO_BINDING_ENTRY).toSet()) {
				deleteBinding(iParentId, pair.getKey(), pair.getValue());
				// TODO: if it fails, recover and create the remaining ones?
				createNewKeyBinding(pair.getValue(), pair.getKey(), iParentId);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(getKeys(iParentId).toString())
					.type("application/json").build();
		}

		private static void deleteBinding(Integer iParentId, String key, String name) {
			try {
				execute("START parent=node( {parentId} ) MATCH parent-[r:CONTAINS]->category WHERE has(category.key) and category.type = 'categoryNode' and category.key = {key} DELETE category.key RETURN category",
						ImmutableMap.<String, Object> builder()
								.put("parentId", iParentId)
								.put("key", key).build(), "deleteBinding()");
				System.out.println("deleteBinding() - Removed keybinding for " + name);
			} catch (Exception e) {
				System.out.println("deleteBinding() - Did not removed keybinding for " + name + " since there currently isn't one.");
			}
		}
		
		private static final Function<String, Map.Entry<String, String>> LINE_TO_BINDING_ENTRY = new Function<String, Map.Entry<String, String>>() {
			@Override
			public Entry<String, String> apply(String bindingLine) {
				String[] aLineElements = bindingLine.split("=");
				// Ignore trailing comments
				return new AbstractMap.SimpleEntry<String, String>(aLineElements[0].trim(),
						aLineElements[1].trim().split("#")[0].trim());
			}
		};

		private static final Predicate<String> IS_COMMENTED = new Predicate<String>(){
			@Override
			public boolean apply(@Nullable String aNewKeyBinding) {
				if (aNewKeyBinding == null) {
					return true;
				}
				if (aNewKeyBinding.equals("")){
					return true;
				}
				if (aNewKeyBinding.trim().startsWith("#")
						&& !aNewKeyBinding.trim().startsWith("#=")) {
					return true;
				}
				if (aNewKeyBinding.trim().startsWith("_")) {
					// do not allow key binding that is "_". This is reserved
					// for hiding until refresh
					return true;
				}
				return false;
			}
		};
		
		private Set<String> difference(String iNewKeyBindings,
				String iOldKeyBindings) {
			// NOTE: This is not symmetric (commutative?). If you want to
			// support removal do that in a separate loop
			Set<String> theNewKeyBindingLines = Sets.difference(
					ImmutableSet.copyOf(iNewKeyBindings.trim().split("\n")),
					ImmutableSet.copyOf(iOldKeyBindings.trim().split("\n")));
			System.out.println("Difference: " + theNewKeyBindingLines);
			return theNewKeyBindingLines;
		}

		private static void createNewKeyBinding(String iCategoryName, String iKeyCode,
				Integer iParentId) throws IOException, JSONException {
			System.out.println("createNewKeyBinding() - begin() : " + String.format("iCategoryName %s\tiKeyCode %s\tiParentId %d", iCategoryName, iKeyCode, iParentId));
			// TODO: Also create a trash category for each new category key node
			JSONArray theCategoryNodes = (JSONArray) execute(
					"START parent=node({parentId})" +
					" MATCH parent -[r:CONTAINS]-> existingCategory" +
					" WHERE has(existingCategory.type)" +
					" and existingCategory.type = 'categoryNode'" +
					" and existingCategory.name = {aCategoryName}" +
					" SET existingCategory.key = {aKeyCode}" +
					" RETURN distinct id(existingCategory)",
					ImmutableMap.<String, Object> builder()
							.put("parentId", iParentId)
							// TODO: change this back
							.put("aCategoryName", iCategoryName)
							.put("aKeyCode", iKeyCode).build(), "createNewKeyBinding()").get("data");
			try {
				createNewRelation(iParentId,
						Integer.parseInt(getCategoryNodeIdString(iCategoryName,
								iKeyCode, theCategoryNodes)));
				System.out.println("createNewKeyBinding() - end()");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/** Get or create the category node that is to be associated with the key code */
		private static String getCategoryNodeIdString(String iCategoryName,
				String iKeyCode, JSONArray theCategoryNodes) throws IOException {
			String theNewCategoryNodeIdString;
			if (shouldCreateNewCategoryNode(theCategoryNodes, iCategoryName)) {
				// TODO: first check if there is already a node with this name,
				// which is for re-associating the keycode with the category
				theNewCategoryNodeIdString = (String) ((JSONArray) ((JSONArray) execute(
						"CREATE (n { name : {name} , key : {key}, created: {created} , type :{type}}) "
								+ "RETURN id(n)",
						ImmutableMap.<String, Object> builder()
								.put("name", iCategoryName)
								.put("key", iKeyCode)
								.put("type", "categoryNode")
								.put("created", System.currentTimeMillis())
								.build(), "getCategoryNodeIdString()").get("data")).get(0)).get(0);
			} else {
				if (theCategoryNodes.length() > 0) {
					if (theCategoryNodes.length() > 1) {
						// Sanity check
						throw new RuntimeException(
								"There should never be 2 child categories of the same node with the same name");
					}
					theNewCategoryNodeIdString = (String) ((JSONArray) theCategoryNodes
							.get(0)).get(0);
				} else {
					theNewCategoryNodeIdString = "-1";
				}
			}
			return theNewCategoryNodeIdString;
		}

		private static boolean shouldCreateNewCategoryNode(JSONArray theCategoryNodes, String iCategoryName) {
			boolean shouldCreateNewCategoryNode = false;
			if (theCategoryNodes.length() > 0) {
				if (theCategoryNodes.length() > 1) {
					// Sanity check
					throw new RuntimeException(
							"There should never be 2 child categories of the same node with the same name");
				}
			} else {
				shouldCreateNewCategoryNode = true;
			}
			return shouldCreateNewCategoryNode;
		}

		@GET
		@Path("keys")
		@Produces("application/json")
		public Response keys(@QueryParam("parentId") Integer iParentId)
				throws JSONException, IOException {
			JSONArray ret = getKeys(iParentId);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}

		// TODO: Rewrite this as a map-fold?
		public static JSONArray getKeys(Integer iParentId) throws IOException,
				JSONException {
//			System.out.println("getKeys() - parent ID: " + iParentId);
			// Unfortunately, we cannot insist on counting only the URL nodes -
			// it will prevent category nodes from being returned. See if
			// there's a way to do this in Cypher. If there isn't, this is not a
			// huge compromise.
			// TODO: find a way to count the nodes
			JSONArray theData = (JSONArray) execute(
					"START parent=node({parentId}) " +
					"MATCH parent-[c:CONTAINS*1..2]->n " +
					"WHERE has(n.name)  and n.type = 'categoryNode' and id(parent) = {parentId} " +
					"RETURN distinct ID(n),n.name,n.key,0 as c order by c desc",
					ImmutableMap.<String, Object> builder()
							.put("parentId", iParentId).build(), "getKeys()").get("data");
			JSONArray oKeys = new JSONArray();
			for (int i = 0; i < theData.length(); i++) {
				JSONArray aBindingArray = theData.getJSONArray(i);
				JSONObject aBindingObject = new JSONObject();
				aBindingObject.put("id", (String) aBindingArray.get(0));
				aBindingObject.put("name", (String) aBindingArray.get(1));
				// TODO: this could be null
				aBindingObject.put("key", (String) aBindingArray.get(2));
				aBindingObject.put("count",
						((Integer) aBindingArray.get(3)).toString());
				oKeys.put(aBindingObject);
			}
//			System.out.println("getKeys() - end. length: " + oKeys.length());
			return oKeys;
		}

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------


		/**
		 * No deletion of existing relationships occurs here.
		 * 
		 * @throws RuntimeException
		 *             - If the command fails. This could legitimately happen if
		 *             we try to relate to a newly created category if the
		 *             system becomes non-deterministic.
		 */
		private static JSONObject createNewRelation(Integer iParentId, Integer iChildId)
				throws IOException, JSONException {
			return execute(
					"START a=node({parentId}),b=node({childId}) "
							+ "CREATE a-[r:CONTAINS]->b SET b.accessed = {currentTime} return a,r,b;",
					ImmutableMap.<String, Object> builder()
							.put("parentId", iParentId)
							.put("childId", iChildId)
							.put("currentTime", System.currentTimeMillis())
							.build(), "createNewRelation()");
		}

		static JSONObject execute(String iCypherQuery, Map<String, Object> iParams, String... iCommentPrefix) {
			return execute(iCypherQuery, iParams, true, iCommentPrefix);
		}

		// TODO: make this map immutable
		static JSONObject execute(String iCypherQuery,
				Map<String, Object> iParams, boolean doLogging, String... iCommentPrefix) {
			String commentPrefix = iCommentPrefix.length > 0 ? iCommentPrefix[0] + " " : "";
			if (doLogging) {
				System.out.println(commentPrefix + " - \t" + iCypherQuery);
				System.out.println(commentPrefix + "- \tparams - " + iParams);
			}
			ClientConfig clientConfig = new DefaultClientConfig();
			clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING,
					Boolean.TRUE);
	
			// POST {} to the node entry point URI
			ClientResponse theResponse = Client.create(clientConfig).resource(
					CYPHER_URI)
					.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).entity("{ }")
					.post(ClientResponse.class, ImmutableMap
							.<String, Object> of("query", iCypherQuery, "params",
									Preconditions.checkNotNull(iParams)));
			if (theResponse.getStatus() != 200) {
				System.out.println(commentPrefix + "FAILED:\n\t" + iCypherQuery + "\n\tparams: " + iParams);
				try {
					throw new RuntimeException(IOUtils.toString(theResponse.getEntityInputStream(), "UTF-8"));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			String theNeo4jResponse ;
			try {
				// Do not inline this. We need to close the stream after
				// copying
				theNeo4jResponse = IOUtils.toString(theResponse.getEntityInputStream(), "UTF-8");
				theResponse.getEntityInputStream().close();
				theResponse.close();
				if (doLogging) {
					System.out.println(commentPrefix + "end");
				}
				return new JSONObject(theNeo4jResponse);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {

		String port = null;
		_parseOptions: {

		  Options options = new Options()
			  .addOption("h", "help", false, "show help.");

		  Option option = Option.builder("f").longOpt("file").desc("use FILE to write incoming data to").hasArg()
			  .argName("FILE").build();
		  options.addOption(option);

		  // This doesn't work with java 7
		  // "hasarg" is needed when the option takes a value
		  options.addOption(Option.builder("p").longOpt("port").hasArg().required().build());

		  try {
			CommandLine cmd = new DefaultParser().parse(options, args);
			port = cmd.getOptionValue("p", "4440");

			if (cmd.hasOption("h")) {
		
			  // This prints out some help
			  HelpFormatter formater = new HelpFormatter();

			  formater.printHelp("yurl", options);
			  System.exit(0);
			}
		  } catch (ParseException e) {
			e.printStackTrace();
			System.exit(-1);
		  }
		}
    
		// Turn off that stupid Jersey logger.
		// This works in Java but not in Groovy.
		//java.util.Logger.getLogger("org.glassfish.jersey").setLevel(java.util.Level.SEVERE);
		try {
			JdkHttpServerFactory.createHttpServer(
					new URI("http://localhost:" + port + "/"), new ResourceConfig(
							YurlResource.class));
			// Do not allow this in multiple processes otherwise your hard disk will fill up
			// or overload the database
			// Problem - this won't get executed until the server ends
			//YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		} catch (Exception e) {
			System.out.println("Not creating server instance");
		}
	}
}
