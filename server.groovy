import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ProcessBuilder.Redirect;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import com.github.axet.vget.VGet;
import com.github.axet.vget.info.VideoInfo.VideoQuality;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

// TODO: Use javax.json.* for immutability
public class Yurl {
	// I hope this is the same as JSONObject.Null (not capitals)
	public static final Object JSON_OBJECT_NULL = JSONObject.Null;
	private static final String CHROMEDRIVER_PATH = //"/Users/sarnobat/trash/chromedriver";
	//"/home/sarnobat/trash/chromedriver";
	"/home/sarnobat/github/yurl/chromedriver";
	
	@Path("yurl")
	public static class HelloWorldResource { // Must be public

		private static final Integer ROOT_ID = 45;
		private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
		private static final String TARGET_DIR_PATH = "/media/sarnobat/Unsorted/Videos/";
		private static final String TARGET_DIR_PATH_IMAGES = "/media/sarnobat/Unsorted/images/";

		static {
//			System.out.println("static() begin");
//we can't put this in the constructor because multiple instances will get created
//			HelloWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		}

		private static JSONObject categoriesTreeCache;

		// This only gets invoked when it receives the first request
		// Multiple instances get created
		HelloWorldResource() {
//			System.out.println("HelloWorldResource() - begin");
			// We can't put the auto downloader in main()
			// then either it will be called every time the cron job is executed,
			// or not until the server terminates unexceptionally (which never happens).
			
		}
		
		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response getUrls(@QueryParam("rootId") Integer iRootId)
				throws JSONException, IOException {
			checkNotNull(iRootId);
			JSONObject categoriesTreeJson;
			if (categoriesTreeCache == null) {
				System.out.println("getUrls() - preloaded categories tree not ready");
				categoriesTreeJson = CategoryTree.getCategoriesTree(ROOT_ID);
			} else {
				categoriesTreeJson = categoriesTreeCache;
				// This is done in a separate thread
				refreshCategoriesTreeCacheInSeparateThread();
			}
			JSONObject retVal = new JSONObject();
			try {
				retVal.put("urls", getItemsAtLevelAndChildLevels(iRootId));
				retVal.put("categoriesRecursive", categoriesTreeJson);
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(retVal.toString())
						.type("application/json").build();
			}
			catch (Exception e) {
				e.printStackTrace();
				return Response.serverError().header("Access-Control-Allow-Origin", "*")
						.entity(e.getStackTrace())
						.type("application/text").build();
			}
		}


		@GET
		@Path("downloadVideo")
		@Produces("application/json")
		public Response downloadVideoSynchronous(@QueryParam("id") Integer iRootId, @QueryParam("url") String iUrl)
				throws JSONException, IOException {
			getVideoDownloadJob(iUrl, TARGET_DIR_PATH,
					Integer.toString(iRootId)).run();
			JSONObject retVal = new JSONObject();
			try {
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(retVal.toString())
						.type("application/json").build();
			}
			catch (Exception e) {
				e.printStackTrace();
				return Response.serverError().header("Access-Control-Allow-Origin", "*")
						.entity(e.getStackTrace())
						.type("application/text").build();
			}
		}

		@GET
		@Path("parent")
		@Produces("application/json")
		public Response parent(@QueryParam("nodeId") Integer iNodeId)
				throws JSONException, IOException {
			ImmutableMap.Builder<String, Object> theParams = ImmutableMap.<String, Object>builder();
			theParams.put("nodeId", iNodeId);
			// TODO: order these by most recent-first (so that they appear this
			// way in the UI)
			JSONObject theParentNodeJson = execute(
					"start n=node({nodeId}) MATCH p-[r:CONTAINS]->n RETURN id(p)",
					theParams.build(), "parent()");
			JSONArray theData = (JSONArray) theParentNodeJson.get("data");
			JSONArray ret = new JSONArray();
			for (int i = 0; i < theData.length(); i++) {
				JSONArray a = theData.getJSONArray(i);
				JSONObject o = new JSONObject();
				String id = (String) a.get(0);
				o.put("id", id);
				ret.put(o);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}

		//
		// Batch
		//

		@GET
		@Path("batchInsert")
		@Produces("application/json")
		public Response batchInsert(@QueryParam("rootId") Integer iRootId,
				@QueryParam("urls") String iUrls) throws Exception {
			Preconditions.checkArgument(iRootId != null);
//			System.out.println("batchInsert - " + iRootId);
			// System.out.println("batchInsert - " + URLDecoder.decode(iUrls,
			// "UTF-8"));
			StringBuffer unsuccessfulLines = new StringBuffer();

			String[] lines = iUrls.trim().split("\\n");
			int i = 0;
			while (i < lines.length) {

//				System.out.println("1");
				String first = lines[i];
				
				String theHttpUrl;
				try {
					if (first.startsWith("http")) {
						stash(URLEncoder.encode(first, "UTF-8"), iRootId);
						++i;
						continue;
					}
					// Fails if it sees the string "%)"
					theHttpUrl = URLDecoder.decode(first, "UTF-8");
				} catch (Exception e) {
					System.out.println(e.getStackTrace());
					addToUnsuccessful(unsuccessfulLines, first, "");
					++i;
					continue;
				}
//				System.out.println("decoded: " + theHttpUrl);
				if (first.startsWith("=")) {
					++i;
					addToUnsuccessful(unsuccessfulLines, first, "");
					continue;
				}
				if (first.startsWith("http")) {
					++i;
					addToUnsuccessful(unsuccessfulLines, first, "");
					continue;
				}
//				System.out.println("3");
				if (first.matches("^\\s*" + '$')) {
					++i;
					continue;
				}
//				System.out.println("4");

				String second = lines[i + 1];
				if (first.startsWith("\"") && second.startsWith("http")) {

					System.out.println("to be processed: " + first);
					System.out.println("to be processed: " + second);

					stash(second, iRootId);

				} else {
					addToUnsuccessful(unsuccessfulLines, first, second);
				}
				i += 2;

			}
			JSONObject entity = new JSONObject();
			entity.put("unsuccessful", unsuccessfulLines.toString());
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(entity.toString()).type("application/json").build();
		}

		// TODO : remove mutable state
		public void addToUnsuccessful(StringBuffer unsuccessfulLines,
				String first, String second) {
			unsuccessfulLines.append(first);
			unsuccessfulLines.append("\n");
			unsuccessfulLines.append(second);
			unsuccessfulLines.append("\n");
			unsuccessfulLines.append("\n");
		}

		//
		// ------------------------------------------------------------------------------------
		// Backup
		// ------------------------------------------------------------------------------------

		@GET
		@Path("dumpurls")
		@Produces("application/text")
		public Response dumpUrls(@QueryParam("rootId") Integer iRootId)
				throws IOException, JSONException {
			Set<String> visitedInternalNodes = new HashSet<String>();
			Integer startId = iRootId;
			if (startId == null) {
				startId = 0;
			}
			StringBuffer sb = new StringBuffer();
			StringBuffer plainText = new StringBuffer();
			sb.append("foo\n");
			printNode(startId, sb, plainText, visitedInternalNodes);
			System.out.println("dumpUrls");
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(plainText.toString()).type("text/plain").build();
		}

		private void printNode(Integer iRootId, StringBuffer json,
				StringBuffer plainText, Set<String> visitedInternalNodes)
				throws IOException, JSONException {
			json.append("bar");
			ImmutableMap.Builder<String, Object> theParams = ImmutableMap.<String, Object>builder();
			theParams.put("nodeId", iRootId);
			JSONObject theResponse = execute(
					"start root=node({nodeId}) MATCH root--n RETURN distinct n, id(n)",
					theParams.build(), "printNode()");
			JSONArray jsonArray = (JSONArray) theResponse.get("data");
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONArray aNode = (JSONArray) jsonArray.get(i);
				JSONObject object = (JSONObject) ((JSONObject) aNode.get(0))
						.get("data");

				String id = (String) checkNotNull(aNode.get(1));
				if (visitedInternalNodes.contains(id)) {
					continue;
				}
				System.out.println("1.5");

				if (object.has("type") && object.get("type") != null
						&& object.get("type").equals("categoryNode")) {
					plainText.append("=== ");
					plainText.append(object.get("name"));
					plainText.append(" ===\n");

				}

				if (object.has("title")) {
					plainText.append("\"");
					plainText.append(object.get("title"));
					plainText.append("\",\"");
					if (object.has("url")) {
						plainText.append(object.get("url"));
					}
					plainText.append("\"");
					plainText.append("\n");
				}
				if (object.has("url")) {
					plainText.append(object.get("url"));
					plainText.append("\n");
					plainText.append("\n");
				}

				json.append(object);
				System.out.println("2");
				visitedInternalNodes.add(id);

				printNode(Integer.parseInt(id), json, plainText,
						visitedInternalNodes);
			}
		}

		// ------------------------------------------------------------------------------------
		// Page operations
		// ------------------------------------------------------------------------------------

		@GET
		@Path("count_non_recursive")
		@Produces("application/json")
		// It's better to do this in a separate Ajax request because it's fast and we can get an idea if database queries are working.
		public Response countNonRecursive(@QueryParam("rootId") Integer iRootId)
				throws Exception {
			checkNotNull(iRootId);
			try {
				ImmutableMap.Builder<String, Object> theParams = ImmutableMap.<String, Object>builder();
				theParams.put("rootId", iRootId);
				JSONObject theQueryResultJson = execute(
						"start n=node({rootId}) optional match n-[CONTAINS]->u where has(u.title) return n.name, count(u) as cnt",
						theParams.build());
				JSONArray outerArray = (JSONArray) theQueryResultJson
						.get("data");
				JSONArray innerArray = (JSONArray) outerArray.get(0);
				String name = (String) innerArray.get(0);
				Integer count = (Integer) innerArray.get(1);
				JSONObject result = new JSONObject();
				result.put("count", count);
				result.put("name", name);
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(result.toString()).type("application/json")
						.build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		////
		//// The main part
		////
		// TODO: See if you can turn this into a map-reduce
		private static JSONObject getItemsAtLevelAndChildLevels(Integer iRootId) throws JSONException, IOException {
//			System.out.println("getItemsAtLevelAndChildLevels() - " + iRootId);
			if (categoriesTreeCache == null) {
				categoriesTreeCache = CategoryTree.getCategoriesTree(ROOT_ID);
			}
			// TODO: the source is null clause should be obsoleted
			JSONObject theQueryResultJson = execute(
					"START source=node({rootId}) "
							+ "MATCH p = source-[r:CONTAINS*1..2]->u "
							+ "WHERE (source is null or ID(source) = {rootId}) and not(has(u.type)) AND id(u) > 0  "
							+ "RETURN distinct ID(u),u.title,u.url, extract(n in nodes(p) | id(n)) as path,u.downloaded_video,u.downloaded_image,u.created,u.ordinal, u.biggest_image, u.user_image "
// TODO : do not hardcode the limit to 500. Category 38044 doesn't display more than 50 books since there are so many child items.
							+ "ORDER BY u.ordinal DESC LIMIT 500", ImmutableMap
							.<String, Object> builder().put("rootId", iRootId)
							.build(), "getItemsAtLevelAndChildLevels()");
			JSONArray theDataJson = (JSONArray) theQueryResultJson.get("data");
			JSONArray theUncategorizedNodesJson = new JSONArray();
			for (int i = 0; i < theDataJson.length(); i++) {
				JSONObject anUncategorizedNodeJsonObject = new JSONObject();
				_1: {
					JSONArray anItem = theDataJson.getJSONArray(i);
					_11: {
						String anId = (String) anItem.get(0);
						anUncategorizedNodeJsonObject.put("id", anId);
					}
					_12: {
						String aTitle = (String) anItem.get(1);
						anUncategorizedNodeJsonObject.put("title", aTitle);
					}
					_13: {
						String aUrl = (String) anItem.get(2);
						anUncategorizedNodeJsonObject.put("url", aUrl);
					}
					_14: {
						try {
							JSONArray path = (JSONArray) anItem.get(3);
							if (path.length() == 3) {
								anUncategorizedNodeJsonObject.put("parentId",
										path.get(1));
							} else if (path.length() == 2) {
								anUncategorizedNodeJsonObject.put("parentId",
										iRootId);
							}
							else if (path.length() == 1) {
								// This should never happen
								anUncategorizedNodeJsonObject.put("parentId",
										path.get(0));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					_15: {

						Object val = anItem.get(4);
						if (isNotNull(val)) {
							String aValue = (String) val;
							anUncategorizedNodeJsonObject.put("downloaded_video", aValue);
						}
					}
					_16: {
						Object val = anItem.get(6);
						if ("null".equals(val)) {
							System.out.println("Is null value");
						} else if (val == null) {
							System.out.println("Is null string");
						} else if (isNotNull(val)) {
							Long aValue = (Long) val;
							anUncategorizedNodeJsonObject.put("created", aValue);
						}
					}
					_17: {
						Object val = anItem.get(8);
						if (isNotNull(val)) {
							String aValue = (String) val;
							if ("null".equals(aValue)) {
								System.out.println("HelloWorldResource.getItemsAtLevelAndChildLevels() - does this ever occur?");
							}
							anUncategorizedNodeJsonObject.put("biggest_image", aValue);
						}
					}
					_18: {
						Object val = anItem.get(9);
						if (isNotNull(val)) {
							String aValue = (String) val;
							if ("null".equals(aValue)) {
								System.out.println("HelloWorldResource.getItemsAtLevelAndChildLevels() - does this ever occur?");
							}
							anUncategorizedNodeJsonObject.put("user_image", aValue);
						}
					}
				}
				theUncategorizedNodesJson.put(anUncategorizedNodeJsonObject);
			}
			
			JSONObject ret = new JSONObject();
			transform : {
				for (int i = 0; i < theUncategorizedNodesJson.length(); i++) {
					JSONObject jsonObject = (JSONObject) theUncategorizedNodesJson
							.get(i);
					String parentId = (String) jsonObject.get("parentId");
					if (!ret.has(parentId)) {
						ret.put(parentId, new JSONArray());
					}
					JSONArray target = (JSONArray) ret.get(parentId);
					target.put(jsonObject);
				}
			}
			return ret;
		}

		private static boolean isNotNull(Object val) {
			return val != null && !("null".equals(val)) && !(val.getClass().equals(Yurl.JSON_OBJECT_NULL));
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

		@GET
		@Path("stash")
		@Produces("application/json")
		public Response stash(@QueryParam("param1") String iUrl,
				@QueryParam("rootId") Integer iRoodId) throws JSONException,
				IOException {
//			System.out.println("stash() - begin");
			// This will convert
			String theHttpUrl = URLDecoder.decode(iUrl, "UTF-8");
//			System.out.println("stash() - url decoded: " + theHttpUrl);
			String theTitle = getTitle(new URL(theHttpUrl));
			try {
				JSONObject newNodeJsonObject = createNode(theHttpUrl, theTitle,
						new Integer(iRoodId));
//				System.out.println("stash() - node created");
//				System.out.println("About to get id");
				JSONArray theNewNodeId = (JSONArray) ((JSONArray) newNodeJsonObject
						.get("data")).get(0);
//				System.out.println("Got array: " + theNewNodeId);
//				System.out.println(theNewNodeId.get(0));
				String id = (String) theNewNodeId.get(0);
				System.out.println(id);

				launchAsynchronousTasks(theHttpUrl, id);
				// TODO: check that it returned successfully (redundant?)
//				System.out.println(newNodeJsonObject.toString());
				System.out.println("stash() - node created: " + id);
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(newNodeJsonObject.get("data").toString())
						.type("application/json").build();
			} catch (Exception e) {
				System.out.println("error");
				System.out.println(e);
				e.printStackTrace();
				//return null;
				throw new JSONException(e);
			}
		}

		private static void launchAsynchronousTasks(String iUrl, String id) {
			downloadImageInSeparateThread(iUrl, TARGET_DIR_PATH_IMAGES,
					CYPHER_URI, id);
			downloadVideoInSeparateThread(iUrl, TARGET_DIR_PATH, CYPHER_URI, id);
			recordBiggestImage(iUrl, CYPHER_URI, id);
		}
		
		private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

		private static void recordBiggestImage(final String iUrl, final String cypherUri, final String id) {
//			System.out.println("HelloWorldResource.recordBiggestImage() - begin");
//			Callable<String> callable = new Callable<String>() {
//				public String call() {
//					return HelloWorldResource.getBiggestImage(iUrl);
//				}
//
//				
//			};
//			String biggestImageAbsUrl = HelloWorldResource.BiggestImage
//					.getBiggestImage("http://www.denimblog.com/2015/07/stella-maxwell-in-rag-bone/");
//			System.out.println("HelloWorldResource.recordBiggestImage()" + " - Biggest image is: " + biggestImageAbsUrl);
			Runnable r = new Runnable() {
				
				@Override
				public void run() {
					execute("start n=node({id}) SET n.biggest_image = {biggestImage}",
							ImmutableMap.<String, Object> of("id", Integer.parseInt(id),
									"biggestImage", HelloWorldResource.getBiggestImage(iUrl)), "recordBiggestImage()");		
				}
			};
//			FutureTask<String> future = new FutureTask<String>(callable);
			executorService.execute(r);
			
		}

		private static String getBiggestImage(final String iUrl2) {
//			System.out.println("HelloWorldResource.getBiggestImage() - begin");
			String biggestImageAbsUrl = null;
			try {
				biggestImageAbsUrl = BiggestImage.getBiggestImage(iUrl2);
				return biggestImageAbsUrl;
			} catch (MalformedURLException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

		private static String encode(String value) {
			String encode = null;
			try {
				encode = URLEncoder.encode(value, "UTF-8");
			} catch (NumberFormatException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return encode;
		}

		private static String getValue(FutureTask<String> future) {
			String s;
			try {
				s = future.get();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				throw new RuntimeException(e1);
			} catch (ExecutionException e1) {
				e1.printStackTrace();
				throw new RuntimeException(e1);
			}
			return s;
		}

		private static void execute2(String iCypherQuery, Map<String, Object> of) {
			String string = "recordBiggestImage()";
			try {
				HelloWorldResource.execute(iCypherQuery, of, string);
			} catch (JSONException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

		private static void downloadImageInSeparateThread(final String iUrl2,
				final String targetDirPath, final String cypherUri,
				final String id) {
			final String iUrl = iUrl2.replaceAll("\\?.*","");
			Runnable r = new Runnable() {
				// @Override
				public void run() {
					System.out.println("downloadImageInSeparateThread() - "
							+ iUrl + " :: " + targetDirPath);
					if (iUrl.toLowerCase().contains(".jpg")) {
					} else if (iUrl.toLowerCase().contains(".jpeg")) {
					} else if (iUrl.toLowerCase().contains(".png")) {
					} else if (iUrl.toLowerCase().contains(".gif")) {
					} else if (iUrl.toLowerCase().contains("gstatic")) {
					} else {
						return;
					}
					try {
						saveImage(iUrl, targetDirPath);
						execute("start n=node({id}) WHERE n.url = {url} SET n.downloaded_image = {date}",
								ImmutableMap.<String, Object> of("id",
										Long.valueOf(id), "url", iUrl, "date",
										System.currentTimeMillis()), "downloadImageInSeparateThread()");
						System.out.println("downloadImageInSeparateThread() - DB updated");
					} catch (Exception e) {
						// e.printStackTrace();
						System.out.println(e.getMessage());
					}
				}
			};
			new Thread(r).start();
		}

		private static void saveImage(String urlString, String targetDirPath)
				throws IllegalAccessError, IOException {
			System.out.println("saveImage() - " + urlString + "\t::\t"
					+ targetDirPath);
			String extension = FilenameUtils.getExtension(urlString);
			ImageIO.write(
					ImageIO.read(new URL(urlString)),
					extension,
					new File(determineDestinationPathAvoidingExisting(
							targetDirPath
									+ "/"
									+ URLDecoder.decode(
											FilenameUtils
													.getBaseName(urlString)
													.replaceAll("/", "-"),
											"UTF-8") + "." + extension)
							.toString()));

		}

		private static java.nio.file.Path determineDestinationPathAvoidingExisting(
				String destinationFilePath) throws IllegalAccessError {
			String destinationFilePathWithoutExtension = destinationFilePath
					.substring(0, destinationFilePath.lastIndexOf('.'));
			String extension = FilenameUtils.getExtension(destinationFilePath);
			java.nio.file.Path destinationFile = Paths.get(destinationFilePath);
			while (Files.exists(destinationFile)) {
				destinationFilePathWithoutExtension += "1";
				destinationFilePath = destinationFilePathWithoutExtension + "." + extension;
				destinationFile = Paths.get(destinationFilePath);
			}
			if (Files.exists(destinationFile)) {
				throw new IllegalAccessError(
						"an existing file will get overwritten");
			}
			return destinationFile;
		}

		private JSONObject createNode(String theHttpUrl, String theTitle,
				Integer rootId) throws IOException, JSONException {
			long currentTimeMillis = System.currentTimeMillis();
			JSONObject json = execute(
					"CREATE (n { title : {title} , url : {url}, created: {created}, ordinal: {ordinal} }) " +
					"RETURN id(n)",
					ImmutableMap.<String, Object> builder()
							.put("url", theHttpUrl).put("title", theTitle)
							.put("created", currentTimeMillis)
							.put("ordinal", currentTimeMillis).build(), "createNode()");
			createNewRelation(rootId,
					(Integer) ((JSONArray) ((JSONArray) json.get("data"))
							.get(0)).get(0));
			return json;
		}

		private String getTitle(final URL iUrl) {
			String title = "";
			try {
				title = Executors
						.newFixedThreadPool(2)
						.invokeAll(
								ImmutableSet.<Callable<String>> of(new Callable<String>() {
									public String call() throws Exception {
										return Jsoup.connect(iUrl.toString())
												.get().title();
									}
								}), 3000L, TimeUnit.SECONDS).get(0).get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			return title;
		}

		private static void downloadVideoInSeparateThread(
				final String iVideoUrl, final String TARGET_DIR_PATH,
				final String cypherUri, final String id) {
			System.out.println("Yurl.HelloWorldResource.downloadVideoInSeparateThread() - begin: " + iVideoUrl);
			_1: {
			// VGet stopped working
//				Runnable r = new Runnable() {
//
//					// @Override
//					public void run() {
//						downloadVideo(iVideoUrl, TARGET_DIR_PATH, id);
//					}
//				};
//				new Thread(r).start();
			}
			_2: {
				Runnable r2 = getVideoDownloadJob(iVideoUrl, TARGET_DIR_PATH, id);
				executorService.submit(r2);
//				new Thread(r2).run();
			}
		}

		private static Runnable getVideoDownloadJob(final String iVideoUrl, final String TARGET_DIR_PATH,
				final String id) {
			Runnable r2 = new Runnable() {
				@Override
				public void run() {
					_3: {
						Process p = new ProcessBuilder()
								.directory(Paths.get(TARGET_DIR_PATH).toFile())
								.command(ImmutableList.of("/home/sarnobat/bin/youtube_download", iVideoUrl)).inheritIO().start();
						p.waitFor();
						if (p.exitValue() == 0) {
							System.out.println("HelloWorldResource.downloadVideoInSeparateThread() - successfully downloaded " + iVideoUrl);
							writeSuccessToDb(iVideoUrl, id);
						} else {
							System.out.println("HelloWorldResource.downloadVideoInSeparateThread() - error downloading " + iVideoUrl);
						}
					}
				}
			};
			return r2;
		}

		private static void downloadUndownloadedVideosInSeparateThread() {
			System.out.println("downloadUndownloadedVideosInSeparateThread() - begin");
//			Timer timer = new Timer ();
//			TimerTask hourlyTask = new TimerTask() {
//				@Override
//				public void run() {
//					try {
//						downloadUndownloadedVideosBatch();
//					} catch (JSONException e) {
//						e.printStackTrace();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//				}
//			};
//
//			// schedule the task to run starting now and then every hour...
//			timer.schedule (hourlyTask, 0l, 1000*60*10);
			
//			
			new Thread() {
				public void run() {
					try {
						downloadUndownloadedVideosBatch();

				        while(true) {
				            try {
				                Thread.sleep(1000*60*60);
				                //your code here...
				            } catch (InterruptedException ie) {
				            }
				        }
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}

		@SuppressWarnings("unchecked")
		private static void downloadUndownloadedVideosBatch() throws JSONException, IOException {
			System.out.println("downloadUndownloadedVideosBatch() - begin");
			String query = "start root=node(37658)" +
					" match  root-[CONTAINS*]->n" +
					" where not(has(n.downloaded_video)) OR n.downloaded_video is null " +
					" return n.title, n.downloaded_video, n.url, ID(n)" +
					" LIMIT 20;";
			JSONObject results = execute(query, ImmutableMap.<String, Object>of(), "downloadUndownloadedVideosBatch()");
			JSONArray jsonArray = results.getJSONArray("data");
			for (int i = 0; i < jsonArray.length(); i++) {
				final JSONArray row = jsonArray.getJSONArray(i);
				System.out.println("downloadUndownloadedVideosBatch() - iteration: " + row);
				Object title = row.get(0);
				Object downloaded = row.get(1);
				Object url =  row.get(2);
				Object id = row.get(3);
				if (downloaded == null || downloaded == JSONObject.NULL  || "null".equals(downloaded)) {
					if (url instanceof String) {
						if (id instanceof Integer) {
							System.out.println("downloadUndownloadedVideosBatch() - Starting retroactively downloading: " + title);
							try {
								new SimpleTimeLimiter().callWithTimeout(new Callable() {

									@Override
									public Object call() throws Exception {
										downloadVideo(row.getString(2), TARGET_DIR_PATH,
												Integer.toString(row.getInt(3)));
										return null;

									}
								}, 10, TimeUnit.SECONDS, true);
							} catch (Exception e) {
								System.out.println("Continuing");
								continue;
							}
							System.out.println("downloadUndownloadedVideosBatch() - Finished retroactively downloading: " + title);
						} else {
							System.out.println("id - " + id.getClass());
						}
					} else {
						System.out.println("url - " + url.getClass());
					}
				} else  {
					Long downloaded1 = row.getLong(1);
					System.out.println("downloadUndownloadedVideosBatch() - Already downloaded: " + title + "\t" + downloaded1);
				}
			}
		}
		
		private static void downloadVideo(final String iVideoUrl,
				String targetDirPath, final String id) {
			try {
				downloadVideo(iVideoUrl, targetDirPath);
				writeSuccessToDb(iVideoUrl, id);
				
			} catch (JSONException e) {
				System.out.println("downloadVideo() - ERROR recording download in database");
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

		private static void writeSuccessToDb(final String iVideoUrl, final String id)
				throws IOException {
			execute("start n=node({id}) WHERE n.url = {url} SET n.downloaded_video = {date}",
					ImmutableMap.<String, Object> of("id", Long.valueOf(id), "url", iVideoUrl,
							"date", System.currentTimeMillis()), "downloadVideo()");
			System.out.println("downloadVideo() - Download recorded in database");
		}

		private static void downloadVideo(final String iVideoUrl, String targetDirPath)
				throws MalformedURLException {
			System.out.println("downloadVideo() - Begin: " + iVideoUrl);
			File theTargetDir = new File(targetDirPath);
			if (!theTargetDir.exists()) {
				throw new RuntimeException(
						"Target directory doesn't exist");
			}
			final VGet v = new VGet(new URL(iVideoUrl), theTargetDir);
			v.getVideo().setVideoQuality(VideoQuality.p1080);
			System.out.println("downloadVideo() - " + v.getVideo().getWeb().toString());
			System.out.println("downloadVideo() - " + v.getVideo().getVideoQuality());
			System.out.print("downloadVideo() - If this is the last thing you see, vget is hanging");
			v.download();// If this hangs, make sure you are using
			// vget 1.15. If you use 1.13 I know it
			// hangs
			System.out.println("\ndownloadVideo() - successfully downloaded video at " + iVideoUrl + " (" + v.getVideo().getTitle() + ")");
		}
		
		@GET
		@Path("updateImage")
		@Produces("application/json")
		public Response changeImage(
				@QueryParam("url") String imageUrl,
				@QueryParam("id") Integer nodeIdToChange)
				throws IOException, JSONException {
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(execute(
							"START n=node({nodeIdToChange}) "
									+ "SET n.user_image =  {imageUrl}"
									+ "RETURN n",
							ImmutableMap.<String, Object> of("nodeIdToChange",
									nodeIdToChange, "imageUrl",
									imageUrl), "changeImage()")).type("application/json")
					.build();
		}
		
		// moveup, move up
		@GET
		@Path("surpassOrdinal")
		@Produces("application/json")
		public Response surpassOrdinal(
				@QueryParam("nodeIdToChange") Integer nodeIdToChange,
				@QueryParam("nodeIdToSurpass") Integer nodeIdToSurpass)
				throws IOException, JSONException {
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(execute(
							"START n=node({nodeIdToChange}),n2=node({nodeIdToSurpass}) "
									+ "SET n.ordinal = n2.ordinal + 100 "
									+ "RETURN n.ordinal, n2.ordinal",
							ImmutableMap.<String, Object> of("nodeIdToChange",
									nodeIdToChange, "nodeIdToSurpass",
									nodeIdToSurpass), "surpassOrdinal()")).type("application/json")
					.build();
		}

		@GET
		@Path("undercutOrdinal")
		@Produces("application/json")
		public Response undercutOrdinal(
				@QueryParam("nodeIdToChange") Integer nodeIdToChange,
				@QueryParam("nodeIdToUndercut") Integer nodeIdToUndercut)
				throws IOException, JSONException {
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(execute(
							"START n=node({nodeIdToChange}), n2=node({nodeIdToUndercut}) "
									+ "SET n.ordinal=n2.ordinal - 100 "
									+ "RETURN n.ordinal,n2.ordinal",
							ImmutableMap.<String, Object> of("nodeIdToChange",
									nodeIdToChange, "nodeIdToUndercut",
									nodeIdToUndercut), "undercutOrdinal()").toString())
					.type("application/json").build();
		}

		@GET
		@Path("swapOrdinals")
		@Produces("application/json")
		public Response swapOrdinals(@QueryParam("firstId") Integer iFirstId,
				@QueryParam("secondId") Integer iSecondId) throws IOException,
				JSONException {
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(execute(
							"START n=node({id1}), n2=node({id2}) "
									+ "SET n.temp=n2.ordinal, n2.ordinal=n.ordinal, n.ordinal=n.temp "
									+ "RETURN n.ordinal, n2.ordinal",
							ImmutableMap.<String, Object> of("id1", iFirstId,
									"id2", iSecondId), "swapOrdinals()").toString())
					.type("application/json").build();
		}

		/** No existing relationships get deleted */
		@GET
		@Path("relateCategoriesToItem")
		@Produces("application/json")
		public Response relateCategoriesToItem(
				@QueryParam("nodeId") Integer iNodeToBeTagged,
				@QueryParam("newCategoryIds") String iCategoriesToBeAddedTo)
				throws JSONException, IOException {
			System.out.println("relateCategoriesToItem(): begin");
			JSONArray theCategoryIdsToBeAddedTo = new JSONArray(
					URLDecoder.decode(iCategoriesToBeAddedTo, "UTF-8"));
			for (int i = 0; i < theCategoryIdsToBeAddedTo.length(); i++) {
				System.out.println("relateCategoriesToItem(): "
						+ theCategoryIdsToBeAddedTo.getInt(i) + " --> "
						+ iNodeToBeTagged);
				createNewRelation(theCategoryIdsToBeAddedTo.getInt(i),
						iNodeToBeTagged);
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString())
					.type("application/json").build();
		}

		private Integer createCategory(String iCategoryName) throws IOException {
			// TODO: first check if there is already a node with this name,
			// which is for re-associating the keycode with the category
			return Integer
					.parseInt((String) ((JSONArray) ((JSONArray) execute(
							"CREATE (n { name : {name} , created: {created} , type :{type}}) " +
							"RETURN id(n)",
							ImmutableMap.<String, Object> builder()
									.put("name", iCategoryName)
									.put("type", "categoryNode")
									.put("created", System.currentTimeMillis())
									.build(), "createCategory()").get("data")).get(0)).get(0));
		}

		@GET
		@Path("createAndRelate")
		@Produces("application/json")
		public Response createSubDirAndMoveItem(
				@QueryParam("newParentName") String iNewParentName,
				@QueryParam("childId") Integer iItemId,
				@QueryParam("currentParentId") Integer iCurrentParentId)
				throws JSONException, IOException {
			System.out.println("createSubDirAndMoveItem() - begin");
			JSONObject relateToExistingCategory = relateToExistingCategory(iItemId,
					iCurrentParentId,
					createNewCategoryUnderExistingCategory(iNewParentName, iCurrentParentId));
			Response rResponse =  Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(relateToExistingCategory.toString())
					.type("application/json").build();
			return rResponse;
		}

		private Integer createNewCategoryUnderExistingCategory(String iNewParentName,
				Integer iCurrentParentId) throws IOException {
			// Create new category node
			Integer theNewCategoryNodeIdString = createCategory(iNewParentName);

			// Associate new category with current category
			createNewRelation(iCurrentParentId, theNewCategoryNodeIdString);
			return theNewCategoryNodeIdString;
		}

		private JSONObject relateToExistingCategory(Integer iItemId, Integer iCurrentParentId,
				Integer theNewCategoryNodeIdString) throws IOException {
			// delete any existing contains relationship with the
			// specified existing parent (but not with all parents since we
			// could have a many-to-one contains)
			deleteExistingRelationship(iItemId, iCurrentParentId);
			
			// Relate the item to the new category
			JSONObject moveHelper = createNewRelation(theNewCategoryNodeIdString, iItemId);
			return moveHelper;
		}

		/**
		 * This MOVES a node to a new subcategory. It deletes the relationship
		 * with the existing parent
		 */
		@GET
		@Path("relate")
		@Produces("application/json")
		public Response move(@QueryParam("parentId") Integer iNewParentId,
				@QueryParam("childId") Integer iChildId,
				@QueryParam("currentParentId") Integer iCurrentParentId)
				throws JSONException, IOException {
			JSONObject moveHelper = relateToExistingCategory(iChildId, iCurrentParentId,
					iNewParentId);

			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(moveHelper.toString())
					.type("application/json").build();
		}

		private void deleteExistingRelationship(Integer iChildId, Integer iCurrentParentId)
				throws IOException {
			execute("START oldParent = node({currentParentId}), child = node({childId}) MATCH oldParent-[r:CONTAINS]-child DELETE r",
					ImmutableMap.<String, Object> builder()
							.put("currentParentId", iCurrentParentId)
							.put("childId", iChildId).build(), "deleteExistingRelationship()");
		}

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

		// TODO: make this map immutable
		static JSONObject execute(String iCypherQuery,
				Map<String, Object> iParams, String... iCommentPrefix) throws IOException, JSONException {
			String commentPrefix = iCommentPrefix.length > 0 ? iCommentPrefix[0] + " " : "";
//			System.out.println(commentPrefix + "begin");
			System.out.println(commentPrefix + " - \n\t" + iCypherQuery + "\n\tparams - " + iParams);

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
				System.out.println(commentPrefix + "FAILED:\n\t" + iCypherQuery + "\n\tparams: "
						+ iParams);
				throw new RuntimeException(IOUtils.toString(theResponse.getEntityInputStream()));
			}
			String theNeo4jResponse ;
			_1: {
				// Do not inline this. We need to close the stream after copying
				theNeo4jResponse = IOUtils.toString(theResponse.getEntityInputStream());
				theResponse.getEntityInputStream().close();
				theResponse.close();
			}
			System.out.println(commentPrefix + "end");
			return new JSONObject(theNeo4jResponse);
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
//			ret.put("flat", getFlatListOfSubcategoriesRecursive(iParentId));
			JSONObject categoriesTreeJson;
			if (categoriesTreeCache == null) {
				categoriesTreeJson = CategoryTree.getCategoriesTree(ROOT_ID);
			} else {
				categoriesTreeJson = categoriesTreeCache;
				refreshCategoriesTreeCacheInSeparateThread();
			}
			ret.put("categoriesTree", categoriesTreeJson);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(ret.toString()).type("application/json").build();
		}

		private static void refreshCategoriesTreeCacheInSeparateThread() {
			new Thread(){
				@Override
				public void run() {
//					System.out.println("refreshCategoriesTreeCacheInSeparateThread() - run - started");
					try {
						categoriesTreeCache = CategoryTree.getCategoriesTree(ROOT_ID);
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
		
		private static class CategoryTree {
			static JSONObject getCategoriesTree(Integer rootId)
					throws JSONException, IOException {
				return new AddSizes(
						// This is the expensive query, not the other one
						getCategorySizes(HelloWorldResource.execute(
								"START n=node(*) MATCH n-->u WHERE has(n.name) "
										+ "RETURN id(n),count(u);",
								ImmutableMap.<String, Object>of(),
								"getCategoriesTree() [The expensive query]").getJSONArray(
								"data")))
						.apply(
						// Path to JSON conversion done in Cypher
						createCategoryTreeFromCypherResultPaths(
								// TODO: I don't think we need each path do we? We just need each parent-child relationship.
								HelloWorldResource.execute("START n=node({parentId}) "
										+ "MATCH path=n-[r:CONTAINS*]->c "
										+ "WHERE has(c.name) "
										+ "RETURN extract(p in nodes(path)| "
										+ "'{ " + "id : ' + id(p) + ', "
										+ "name : \"'+ p.name +'\" , "
										+ "key : \"' + coalesce(p.key, '') + '\"" + " }'" + ")",
										ImmutableMap.<String, Object> of(
												"parentId", rootId),
										"getCategoriesTree() - [getting all paths 3]"),
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
						if (treePath.get(j).getClass().equals(Yurl.JSON_OBJECT_NULL)) {
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
					if (Yurl.JSON_OBJECT_NULL.equals(iJsonArray.get(i))) {
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
				getParentChildrenMap: {
					for (int pathNum = 0; pathNum < cypherRawResults.length(); pathNum++) {
						JSONArray categoryPath = removeNulls(cypherRawResults.getJSONArray(pathNum).getJSONArray(0));
						for (int hopNum = 0; hopNum < categoryPath.length() - 1; hopNum++) {
							if (categoryPath.get(hopNum).getClass().equals(Yurl.JSON_OBJECT_NULL)) {
								continue;
							}
							if (categoryPath.get(hopNum + 1).getClass().equals(Yurl.JSON_OBJECT_NULL)) {
								continue;
							}
							if (!(categoryPath.get(hopNum + 1) instanceof String)) {
								continue;
							}
							int childId = new JSONObject(
									categoryPath.getString(hopNum + 1)).getInt("id");
							int parentId = checkNotNull(new JSONObject(
									categoryPath
											.getString(hopNum)).getInt("id"));
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

		private static class BiggestImage {

			static String getBiggestImage(String url) throws MalformedURLException, IOException {
//				System.out.println("HelloWorldResource.BiggestImage.getBiggestImage() - begin");
				List<String> imagesDescendingSize = getImagesDescendingSize(url);
//				List<String> imagesOrdered = movePngToEnd(imagesDescendingSize);
				String biggestImage = imagesDescendingSize.get(0);
				return biggestImage;
			}

//			private static List<String> movePngToEnd(List<String> imagesDescendingSize) {
//				List<String> pngFiles = new LinkedList<String>();
//				List<String> nonPngFiles = new LinkedList<String>();
//				for (String filePath : imagesDescendingSize) {
//					if (filePath.matches("(?i).*png$") || filePath.matches("(?i).*png\\?.*")) {
//						pngFiles.add(filePath);
//					} else {
//						nonPngFiles.add(filePath);
//					}
//				}
//				nonPngFiles.addAll(pngFiles);
//				return ImmutableList.copyOf(nonPngFiles); 
//			}

			private static List<String> getImagesDescendingSize(String url) throws MalformedURLException,
					IOException {
				String base = getBaseUrl(url);
				// Don't use the chrome binaries that you browse the web with.
				System.setProperty("webdriver.chrome.driver", Yurl.CHROMEDRIVER_PATH
						);

				WebDriver driver = //new  org.openqa.selenium.htmlunit.HtmlUnitDriver(); 
				//org.openqa.selenium.firefox.FirefoxDriver();
										new ChromeDriver();
				//driver.manage().timeouts().implicitlyWait(180, java.util.concurrent.TimeUnit.SECONDS);
				List<String> ret = ImmutableList.of();
				try {
					driver.get(url);
					// TODO: shame there isn't an input stream, then we wouldn't have to
					// store the whole page in memory
					try {
						// We need to let the dynamic content load.
						Thread.sleep(5000L);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					String source = driver.getPageSource();
					// System.out.println(source);
					List<String> out = getAllTags(base + "/", source);
//					System.out.println("HelloWorldResource.BiggestImage.getImagesDescendingSize() - all img tags: " + out);
					Multimap<Integer, String> imageSizes = getImageSizes(out);
//					System.out.println("HelloWorldResource.BiggestImage.getImagesDescendingSize()" + imageSizes);
					// System.out.println(Joiner.on("\n").join(sortedImages));
					ret = sortByKey(imageSizes);
				} finally {
					driver.quit();
				}
				return ret;
			}

			private static List<String> sortByKey(Multimap<Integer, String> imageSizes) {
				ImmutableList.Builder<String> finalList = ImmutableList.builder();
				
				// Phase 1: Sort by size descending
				ImmutableList<Integer> sortedList = FluentIterable.from(imageSizes.keySet())
						.toSortedList(Ordering.natural().reverse());
				
				// Phase 2: Put JPGs first 
				for (Integer size : sortedList) {
					for (String url: imageSizes.get(size)) {
						if (isJpgFile(url)) {
							finalList.add(url);
							System.out
									.println("BiggestImage.sortByKey() - "
											+ size + "\t" + url);
						}
					}
				}
				for (Integer size : sortedList) {
					for (String url: imageSizes.get(size)) {
						if (!isJpgFile(url)) {
							finalList.add(url);
							System.out
									.println("BiggestImage.sortByKey() - "
											+ size + "\t" + url);
						}
					}
				}
				return finalList.build();
			}

			private static boolean isJpgFile(String url) {
				return url.matches("(?i).*\\.jpg") || url.matches("(?i).*\\.jpg\\?.*");
			}
			
			private static boolean isPngFile(String url) {
				return url.matches("(?i).*\\.png") || url.matches("(?i).*\\.png\\?.*");
			}

			private static Multimap<Integer, String> getImageSizes(List<String> out) {
//				ImmutableMap.Builder<Integer, String> builder = ImmutableMap.builder();
				ImmutableMultimap.Builder<Integer, String> builder = ImmutableMultimap.builder();
				Set<Integer> taken = new HashSet<Integer>();
				for (String imgSrc : out) {
					int size = getByteSize(imgSrc);
//					System.out.println("BiggestImage.getImageSizes() - " + size + "\t" + imgSrc);
					builder.put(size, imgSrc);
//					if (!taken.contains(size)) {
//						builder.put(size, imgSrc);
//						taken.add(size);
//					} else {
//						System.out.println("HelloWorldResource.BiggestImage.getImageSizes() - size already taken: " + size);
//					}
				}
				return builder.build();
			}

			private static int getByteSize(String absUrl) {
				if (Strings.isNullOrEmpty(absUrl)) {
					return 0;
				}
				URL url;
				try {
					url = new URL(absUrl);
					int contentLength = url.openConnection().getContentLength();
					// System.out.println(contentLength + "\t"+ absUrl);
					return contentLength;
				} catch (MalformedURLException e) {
					System.out.println("HelloWorldResource.BiggestImage.getByteSize() - " + absUrl);
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return 0;
			}

			private static String getBaseUrl(String url1) throws MalformedURLException {
				System.out.println("HelloWorldResource.BiggestImage.getBaseUrl() -\t"+url1);
				URL url = new URL(url1);
				String file = url.getFile();
				String path;
				if (file.length() == 0) {
					path = url1;
				} else {
					path = url.getFile().substring(0, file.lastIndexOf('/'));
				}
				String string = url.getProtocol() + "://" + url.getHost() + path;
				System.out.println("HelloWorldResource.BiggestImage.getBaseUrl() -\t"+string);
				return string;
			}

			private static List<String> getAllTags(String baseUrl, String source) throws IOException {
//				System.out.println("HelloWorldResource.BiggestImage.getAllTags() - base URL : " + baseUrl);
				Document doc = Jsoup.parse(IOUtils.toInputStream(source), "UTF-8", baseUrl);
//				System.out.println("HelloWorldResource.BiggestImage.getAllTags() - Entire document: " + doc.toString());
				Elements tags = doc.getElementsByTag("img");
				return FluentIterable.<Element> from(tags).transform(IMG_TO_SOURCE).toList();
			}

			private static final Function<Element, String> IMG_TO_SOURCE = new Function<Element, String>() {
				@Override
				public String apply(Element e) {
					return e.absUrl("src");
				}
			};
		}

		private static class AddChildren implements Function<Map.Entry<Integer, JSONObject>, Map.Entry<Integer,JSONObject>> {
			private final Multimap<Integer, Integer> parentIdToChildrenIdList;
			private final Map<Integer, JSONObject> idToCategoryNode; 
			AddChildren(Multimap<Integer, Integer> parentIdToChildrenIdList, Map<Integer, JSONObject> idToCategoryNode) {
				this.parentIdToChildrenIdList = parentIdToChildrenIdList;
				this.idToCategoryNode = idToCategoryNode;
			}
			@Override
			public Map.Entry<Integer,JSONObject> apply(Map.Entry<Integer,JSONObject> categoryNodeWithoutChildren) {
				Collection<Integer> childCategoryIds = (Collection<Integer>) parentIdToChildrenIdList
						.get(categoryNodeWithoutChildren.getKey());
				if (childCategoryIds == null) {
				} else {
					JSONArray childCategoryNodes = new JSONArray();
					for (Integer childId : childCategoryIds) {
						childCategoryNodes.put(idToCategoryNode.get(childId));
					}
					categoryNodeWithoutChildren.getValue().put("children", childCategoryNodes);
				}
				return categoryNodeWithoutChildren;
			}
		};

		@Deprecated
		// This gives a flat list
		private JSONArray getFlatListOfSubcategoriesRecursive(Integer iParentId)
				throws IOException {
			System.out.println("DEPRECATED::getFlatListOfSubcategoriesRecursive() - begin");
			JSONArray theData = (JSONArray) execute(
					"START parent=node({parentId}) " +
					"MATCH parent-[c:CONTAINS*]->n " +
					"WHERE has(n.name) and n.type = 'categoryNode' and id(parent) = {parentId} " +
					"RETURN distinct ID(n),n.name",
					ImmutableMap.<String, Object> of("parentId", iParentId), "getFlatListOfSubcategoriesRecursive()")
					.get("data");
			JSONArray oKeys = new JSONArray();
			for (int i = 0; i < theData.length(); i++) {
				JSONObject aBindingObject = new JSONObject();
				JSONArray aBindingArray = theData.getJSONArray(i);
				String id = (String) aBindingArray.get(0);
				aBindingObject.put("id", id);
				String title = (String) aBindingArray.get(1);
				aBindingObject.put("name", title);
				oKeys.put(aBindingObject);
			}
			return oKeys;
		}
	}

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
//		String biggestImageAbsUrl = HelloWorldResource.BiggestImage.getBiggestImage("http://www.teamtalk.com/liverpool");
//		String biggestImageAbsUrl = HelloWorldResource.BiggestImage.getBiggestImage("http://www.denimblog.com/2015/07/stella-maxwell-in-rag-bone/");
//		String biggestImageAbsUrl = HelloWorldResource.BiggestImage.getBiggestImage("http://www.lfchistory.net/Articles/Article/61");
//		String biggestImageAbsUrl = HelloWorldResource.BiggestImage.getBiggestImage("http://www.imdb.com/title/tt2484460/");
//		String biggestImageAbsUrl = HelloWorldResource.BiggestImage.getBiggestImage("http://www.midatlanticwrestling.net/resourcecenter/gateway_remembers/uswrestlingclub/ringside_1-3.htm");
//		System.out.println("Biggest image is: " + biggestImageAbsUrl);

		//		HelloWorldResource.downloadVideo("https://www.youtube.com/watch?v=ugf4-sl7tnQ", "/media/sarnobat/Unsorted/Videos/");

		HelloWorldResource.refreshCategoriesTreeCacheInSeparateThread();
		try {
			JdkHttpServerFactory.createHttpServer(
					new URI("http://localhost:4447/"), new ResourceConfig(
							HelloWorldResource.class));
			// Do not allow this in multiple processes otherwise your hard disk will fill up
			// or overload the database
			// Problem - this won't get executed until the server ends
			//HelloWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		} catch (Exception e) {
			System.out.println("Not creating server instance");
		}
	}
}
