import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

// TODO: Use javax.json.* for immutability
public class YurlCategoriesRecursive {

	// Gets stored here: http://192.168.1.2:28017/cache/items/
	public static final String YOUTUBE_DOWNLOAD = "/home/sarnobat/bin/youtube_download";
	public static final Integer ROOT_ID = 45;
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	
	@Path("yurl")
	// TODO: Rename to YurlResource
	public static class YurlResource { // Must be public


		static {
			// We can't put this in the constructor because multiple instances will get created
			// YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		}

		private static JSONObject categoriesTreeCache;

		// This only gets invoked when it receives the first request
		// Multiple instances get created
		YurlResource() {
			// We can't put the auto downloader in main()
			// then either it will be called every time the cron job is executed,
			// or not until the server terminates unexceptionally (which never happens).
		}
		
		
		// ------------------------------------------------------------------------------------
		// Page operations
		// ------------------------------------------------------------------------------------

		private static boolean isNotNull(Object val) {
			return val != null && !("null".equals(val)) && !(val.getClass().equals(YurlResource.JSON_OBJECT_NULL));
		}

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------


		static JSONObject execute(String iCypherQuery, Map<String, Object> iParams, String... iCommentPrefix) {
			execute(iCypherQuery, iParams, true, iCommentPrefix);
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

		// ----------------------------------------------------------------------------
		// Read operations
		// ----------------------------------------------------------------------------
		
		@GET
		@Path("categoriesRecursive")
		@Produces("application/json")
		public Response categoriesRecursive(
				@QueryParam("parentId") Integer iParentId)
				throws JSONException, IOException {
			JSONObject ret = new JSONObject();
			JSONObject categoriesTreeJson;
			java.nio.file.Path path = Paths.get("/home/sarnobat/github/.cache/" + YurlCategoriesRecursive.ROOT_ID + ".json");
			if (Files.exists(path)) {
				categoriesTreeJson = new JSONObject(FileUtils.readFileToString(path.toFile(), "UTF-8"));
			} else {
				if (categoriesTreeCache == null) {
					categoriesTreeJson = ReadCategoryTree.getCategoriesTree(YurlCategoriesRecursive.ROOT_ID);
				} else {
					categoriesTreeJson = categoriesTreeCache;
					refreshCategoriesTreeCacheInSeparateThread();
				}
				FileUtils.writeStringToFile(path.toFile(), categoriesTreeJson.toString(2), "UTF-8");
			}
			ret.put("categoriesTree", categoriesTreeJson);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}

		private static void refreshCategoriesTreeCacheInSeparateThread() {
			new Thread(){
				@Override
				public void run() {
					try {
						categoriesTreeCache = ReadCategoryTree.getCategoriesTree(YurlCategoriesRecursive.ROOT_ID);
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
		
		private static class ReadCategoryTree {
			static JSONObject getCategoriesTree(Integer rootId)
					throws JSONException, IOException {
				return new AddSizes(
						// This is the expensive query, not the other one
						getCategorySizes(YurlResource.execute(
								"START n=node(*) MATCH n-->u WHERE has(n.name) "
										+ "RETURN id(n),count(u);",
								ImmutableMap.<String, Object>of(),
								"getCategoriesTree() - Getting sizes [The expensive query]").getJSONArray(
								"data")))
						.apply(
						// Path to JSON conversion done in Cypher
						createCategoryTreeFromCypherResultPaths(
								// TODO: I don't think we need each path do we? We just need each parent-child relationship.
								YurlResource.execute("START n=node({parentId}) "
										+ "MATCH path=n-[r:CONTAINS*]->c "
										+ "WHERE has(c.name) "
										+ "RETURN extract(p in nodes(path)| "
										+ "'{ " + "id : ' + id(p) + ', "
										+ "name : \"'+ p.name +'\" , "
										+ "key : \"' + coalesce(p.key, '') + '\"" + " }'" + ")",
										ImmutableMap.<String, Object> of(
												"parentId", rootId),
												false,
										"getCategoriesTree() - Getting all paths"),
								rootId));
			}
			
			private static Map<Integer, Integer> getCategorySizes(JSONArray counts) {
				Map<Integer, Integer> sizesMap = new HashMap<Integer, Integer>();
				for (int i = 0; i < counts.length(); i++) {
					JSONArray row = counts.getJSONArray(i);
					sizesMap.put(row.getInt(0), row.getInt(1));
				}
				return ImmutableMap.copyOf(sizesMap);
			}
			

			private static JSONObject addSizesToCypherJsonResultObjects(JSONObject categoriesTree,
					Map<Integer, Integer> categorySizes) {
				Integer id = categoriesTree.getInt("id");
				categoriesTree.put("size", categorySizes.get(id));
				if (categoriesTree.has("children")) {
					JSONArray children = categoriesTree.getJSONArray("children");
					for (int i = 0; i < children.length(); i++) {
						addSizesToCypherJsonResultObjects(children.getJSONObject(i), categorySizes);
					}
				}
				return categoriesTree;
			}
			
			private static class AddSizes implements
					Function<JSONObject, JSONObject> {
				private final Map<Integer, Integer> categorySizes;

				AddSizes(Map<Integer, Integer> categorySizes) {
					this.categorySizes = categorySizes;
				}

				@Override
				public JSONObject apply(JSONObject input) {
					return addSizesToCypherJsonResultObjects(
							input, categorySizes);
				}
			}
			
			private static JSONObject createCategoryTreeFromCypherResultPaths(
					JSONObject theQueryJsonResult, Integer rootId) {
				JSONArray cypherRawResults = theQueryJsonResult
						.getJSONArray("data");
				checkState(cypherRawResults.length() > 0);
				Multimap<Integer, Integer> parentToChildren = buildParentToChildMultimap2(cypherRawResults);
				Map<Integer, JSONObject> categoryNodesWithoutChildren = createId(cypherRawResults);
				JSONObject root = categoryNodesWithoutChildren.get(rootId);
				root.put(
						"children",
						toJsonArray(buildChildren(parentToChildren.get(rootId),
								categoryNodesWithoutChildren, parentToChildren)));
				return root;
			}

			/**
			 * @return - a pair for each category node
			 */
			private static Map<Integer, JSONObject> createId(JSONArray cypherRawResults) {
				ImmutableMap.Builder<Integer, JSONObject> idToJsonBuilder = ImmutableMap
						.<Integer, JSONObject> builder();
				Set<Integer> seen = new HashSet<Integer>();
				for (int i = 0; i < cypherRawResults.length(); i++) {
					JSONArray treePath = cypherRawResults.getJSONArray(i).getJSONArray(0);
					for (int j = 0; j < treePath.length(); j++) {
						if (treePath.get(j).getClass().equals(YurlResource.JSON_OBJECT_NULL)) {
							continue;
						}
						JSONObject pathHopNode = new JSONObject(treePath.getString(j));//treePath.getString(j));
						int categoryId = pathHopNode.getInt("id");
						if (!seen.contains(categoryId)){
							seen.add(categoryId);
							idToJsonBuilder.put(categoryId,
									pathHopNode);
						}
					}
				}
				return idToJsonBuilder.build();
			}
			
			private static JSONArray removeNulls(JSONArray iJsonArray) {
				for(int i = 0; i < iJsonArray.length(); i++) {
					if (YurlResource.JSON_OBJECT_NULL.equals(iJsonArray.get(i))) {
						iJsonArray.remove(i);
						--i;
					}
				}
				return iJsonArray;
			}

			/**
			 * @return Integer to set of Integers
			 */
			private static Multimap<Integer, Integer> buildParentToChildMultimap2(
					JSONArray cypherRawResults) {
				Multimap<Integer, Integer> oParentToChildren = HashMultimap.create();
				for (int pathNum = 0; pathNum < cypherRawResults.length(); pathNum++) {
					JSONArray categoryPath = removeNulls(cypherRawResults.getJSONArray(pathNum)
							.getJSONArray(0));
					for (int hopNum = 0; hopNum < categoryPath.length() - 1; hopNum++) {
						if (categoryPath.get(hopNum).getClass()
								.equals(YurlResource.JSON_OBJECT_NULL)) {
							continue;
						}
						if (categoryPath.get(hopNum + 1).getClass()
								.equals(YurlResource.JSON_OBJECT_NULL)) {
							continue;
						}
						if (!(categoryPath.get(hopNum + 1) instanceof String)) {
							continue;
						}
						int childId = new JSONObject(categoryPath.getString(hopNum + 1))
								.getInt("id");
						int parentId = checkNotNull(new JSONObject(categoryPath.getString(hopNum))
								.getInt("id"));
						Object childrenObj = oParentToChildren.get(parentId);
						if (childrenObj != null) {
							Set<?> children = (Set<?>) childrenObj;
							if (!children.contains(childId)) {
								oParentToChildren.put(parentId, childId);
							}
						} else {
							oParentToChildren.put(parentId, childId);
						}
					}
				}
				return oParentToChildren;
			}

			private static JSONArray toJsonArray(Collection<JSONObject> children) {
				JSONArray arr = new JSONArray();
				for (JSONObject child : children) {
					arr.put(child);
				}
				return arr;
			}

			private static Set<JSONObject> buildChildren(
					Collection<Integer> childIds, Map<Integer, JSONObject> nodes,
					Multimap<Integer, Integer> parentToChildren) {
				Builder<JSONObject> set = ImmutableSet.builder();
				for (int childId : childIds) {
					JSONObject childJson = nodes.get(childId);
					Collection<Integer> grandchildIds = parentToChildren
							.get(childId);
					Collection<JSONObject> grandchildNodes = buildChildren(
							grandchildIds, nodes, parentToChildren);
					JSONArray grandchildrenArray = toJsonArray(grandchildNodes);
					childJson.put("children", grandchildrenArray);
					set.add(childJson);
				}
				return set.build();
			}			
		}

		// I hope this is the same as JSONObject.Null (not capitals)
		@Deprecated // Does not compile in Eclipse, but does compile in groovy
		public static final Object JSON_OBJECT_NULL = JSONObject.Null;//new Null()
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {

		String port = "4441";
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
			port = cmd.getOptionValue("p", "4444");

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
    
		YurlResource.refreshCategoriesTreeCacheInSeparateThread();
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
