import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
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
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

// TODO: Use javax.json.* for immutability
public class YurlList {

	// Gets stored here: http://192.168.1.2:28017/cache/items/
	@Deprecated
	private static final boolean MONGODB_ENABLED = YurlResource.MongoDbCache.ENABLED;
	public static final Integer ROOT_ID = 45;
	@Deprecated
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";

	private static final String YURL_ORDINALS = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_master_ordinals.txt";
	private static final String DOWNLOADED_VIDEOS = System.getProperty("user.home") + "/sarnobat.git/db/auto/yurl_queue_httpcat_videos_downloaded.json";
	private static final String DOWNLOADED_VIDEOS_2017 = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/videos_download_succeeded.txt";
    private static final String QUEUE_DIR = "/home/sarnobat/sarnobat.git/db/yurl_flatfile_db/";
	private static final String QUEUE_FILE_TXT_DELETE = "yurl_deleted.txt";

	private static final String CATEGORY_RELATIONSHIPS = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_category_topology.txt";
	private static final String CATEGORY_NAMES = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_category_names.txt";
	
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
		
		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response getUrls(@QueryParam("rootId") Integer iRootId,
								@QueryParam("enableCache") @DefaultValue("true") Boolean iMongoDbCacheLookupEnabled)
				throws JSONException, IOException {
			checkNotNull(iRootId);
			JSONObject categoriesTreeJson;
			if (categoriesTreeCache == null) {
				System.out.println("getUrls() - preloaded categories tree not ready");
				categoriesTreeJson = ReadCategoryTree.getCategoriesTreeNeo4j(YurlList.ROOT_ID);
			} else {
				categoriesTreeJson = categoriesTreeCache;
				// This is done in a separate thread
				refreshCategoriesTreeCacheInSeparateThread();
			}
			try {
				JSONObject oUrlsUnderCategory;
				// We're not getting up to date pages when things change. But we need to
				// start using this again if we dream of scaling this app.
				if (MONGODB_ENABLED && MongoDbCache.exists(iRootId.toString())) {
					System.out.println("YurlWorldResource.getUrls() - using cache");
					oUrlsUnderCategory = new JSONObject(MongoDbCache.get(iRootId.toString()));
				} else {
					// If there were multiple clients here, you'd need to block the 2nd onwards
					System.out.println("YurlWorldResource.getUrls() - not using cache");
					JSONObject retVal1;
					retVal1 = new JSONObject();
					// TODO: Remove this
					//retVal1.put("urlsNeo4j", getItemsAtLevelAndChildLevelsNeo4j(iRootId));
					
					Collection<String> downloadedVideos = new HashSet(getDownloadedVideos(DOWNLOADED_VIDEOS));
					downloadedVideos.addAll(getDownloadedVideos2017(DOWNLOADED_VIDEOS_2017));
					retVal1.put("urls", getItemsAtLevelAndChildLevels(iRootId, downloadedVideos));
					// TODO: Do we need this? We have server_categoriesRecursive.groovy
					retVal1.put("categoriesRecursive", categoriesTreeJson);
					if (MONGODB_ENABLED) {
						MongoDbCache.put(iRootId.toString(), retVal1.toString());
					}
					oUrlsUnderCategory = retVal1;
				}
				
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(oUrlsUnderCategory.toString())
						.type("application/json").build();
			}
			catch (Exception e) {
				e.printStackTrace();
				return Response.serverError().header("Access-Control-Allow-Origin", "*")
						.entity(e.getStackTrace())
						.type("application/text").build();
			}
		}

		

		// ------------------------------------------------------------------------------------
		// Page operations
		// ------------------------------------------------------------------------------------

		@Deprecated // TODO: this file is not in the right format 
		private Collection<String> getDownloadedVideos(String downloadedVideos) {
			return FileUtils.readLines(Paths.get(downloadedVideos).toFile(), "UTF-8");
		}
		
		private Collection<String> getDownloadedVideos2017(String downloadedVideos) {
			List<String> readLines = FileUtils.readLines(Paths.get(downloadedVideos).toFile(), "UTF-8");
			Set<String> ret = new HashSet<String>();
			for (String line : readLines) {
				String[] elements = line.split("::");
				ret.add(elements[0]);
			}
			return ret;
		}

		private static JSONObject getItemsAtLevelAndChildLevels(Integer iRootId, Collection<String> downloadedVideos) throws JSONException, IOException {
			JSONObject urls = new JSONObject();
		System.out.println("getItemsAtLevelAndChildLevels() begin");	
			Collection<String> categoriesToGetUrlsFrom = ImmutableList
					.<String> builder().add(iRootId.toString())
					.addAll(getChildCategories(iRootId.toString())).build();
			
			Map<String, String> orderMap = buildOrderMap(YURL_ORDINALS,iRootId);

			for (String categoryId : categoriesToGetUrlsFrom) {
				System.out
						.println("YurlList.YurlResource.getItemsAtLevelAndChildLevels() " + categoryId);
				if (categoryId.length() > 10) {
					throw new RuntimeException("Not a category ID: " + categoryId);
				}
				JSONArray urlsInCategory = getUrlsInCategory(categoryId, orderMap, downloadedVideos);
				urls.put(categoryId, urlsInCategory);
			}
			return urls;
		}

		private static Map<String, String> buildOrderMap(String yurlOrdinals,
				Integer iRootId) {

			List<String> lines = FileUtils.readLines(Paths.get(yurlOrdinals).toFile(), "UTF-8");
			Map<String, String> ret = new HashMap<String, String>();
			for (String line : filter(lines, iRootId.toString())) {
				String[] elements = line.split("::");
				ret.put(elements[1], elements[2]);
			}
			
			return ImmutableMap.copyOf(ret);
		}

		private static JSONArray getUrlsInCategory(String categoryId, Map<String, String> orderMap, Collection<String> downloadedVideos) {
			// Create the file if it doesn't exist
			java.nio.file.Path urlsInCategoryJsonFile = Paths.get(System
					.getProperty("user.home") + "/github/yurl/tmp/urls/" + categoryId + ".json");

			if (!urlsInCategoryJsonFile.toFile().exists()) {
				
				JSONArray urlsInCategory = new JSONArray();

				List<String> lines1 = FileUtils.readLines(
						Paths.get(QUEUE_DIR + "/" + QUEUE_FILE_TXT_DELETE)
								.toFile(), "UTF-8");
				
				List<String> remove = getRemoveLines(lines1);
				
				List<String> lines = FileUtils.readLines(
						Paths.get(
								System.getProperty("user.home")
										+ "/sarnobat.git/yurl_master.txt")
								.toFile(), "UTF-8");
				
				
				Map<String, String> userImages = getUserImages(Paths.get(System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_master_images.txt"));

				for (String line : filterByCategory(filterToBeRemovedLines(lines, remove), categoryId)) {
					try {
						JSONObject urlObj = new JSONObject();
						String[] elements = line.split("::");
						if (elements.length < 3) {
							continue;
						}
						//System.err.println("YurlList.YurlResource.getUrlsInCategory(): " + line);
						String categoryIdElement = elements[0];
						String url = elements[1];
						String timestamp = elements[2];
					
						JSONObject urlObj1 = new JSONObject();
						urlObj1.put("id", "STOP_RELYING_ON_THIS");// TODO: moving a url will need reimplementing on the client and server
						urlObj1.put("url", url);
						urlObj1.put("created", Long.parseLong(timestamp));
						if (orderMap.containsKey(url)) {
							urlObj1.put("ordinal", orderMap.get(url));
						} else {
							urlObj1.put("ordinal", Long.parseLong(timestamp));
						}
						if (downloadedVideos.contains(url)) {
							urlObj1.put("downloaded_video", true);
						} else {
							urlObj1.put("downloaded_video", false);
						}
						urlObj1.put("parentId", categoryId);
						urlObj1.put("title", "<fill this in>");
						if (userImages.keySet().contains(url)) {
							urlObj1.put("user_image", userImages.get(url));
						}
					
						urlsInCategory.put(urlObj1);
					} catch (NumberFormatException e) {
						e.printStackTrace();
						continue;
					}
				}
				
				FileUtils.write(urlsInCategoryJsonFile.toFile(), urlsInCategory.toString(2), "UTF-8");
			}
			return new JSONArray(FileUtils.readFileToString(
					urlsInCategoryJsonFile.toFile(), "UTF-8"));
		}

		private static List<String> getRemoveLines(List<String> lines) {
			System.out.println("YurlList.YurlResource.getRemoveLines()");
			List<String> ret = new LinkedList<String>();
			for (String line : lines) {
				if (line.startsWith("-")) {
					ret.add(line);
				}
			}
			return ImmutableList.copyOf(ret);
		}

		private static List<String> filterToBeRemovedLines(List<String> lines,
				List<String> remove1) { // TODO: this should be a HashSet
			List<String> remove = removeField(remove1, 3);
			System.out
					.println("YurlList.YurlResource.filterToBeRemovedLines() remove = " + remove);
			List<String> ret = new LinkedList<String>();
			for (String line : lines) {
				String lineWithout3rdField = removeField(line, 2);
				if (remove.contains("-" + lineWithout3rdField)) {
					System.out.println("YurlList.YurlResource.filterToBeRemovedLines() was removed: " + line);
				} else {
					if (line.startsWith("221013::https://www.amazon.com/ACCO-Binder-Cli")) {
						System.out
								.println("YurlList.YurlResource.filterToBeRemovedLines() ERROR 1: " + line);
						System.out
								.println("YurlList.YurlResource.filterToBeRemovedLines() ERROR 2: " + remove.get(0));
					}
					ret.add(line);
				}
			}
			return ImmutableList.copyOf(ret);
		}

		private static List<String> removeField(List<String> remove1,
				final int i) {
			return FluentIterable.from(remove1)
					.transform(new Function<String, String>() {
						@Override
						@Nullable
						public String apply(@Nullable String input) {
							return removeField(input, i);
						}
					}).toList();
		}

		private static String removeField(String line, int i) {
			String[] e = line.split("::");
			if (e.length < 3) {
				System.err.println("YurlList.YurlResource.removeField() bad line: " + line);
				return line;
			}
			return e[0] + "::" + e[1];
		}

		private static Map<String, String> getUserImages(java.nio.file.Path path) {

			List<String> lines = FileUtils.readLines(path.toFile(), "UTF-8");
			Map<String, String> ret = new HashMap<String, String>();
			for (String line : lines) {
				String[] elements = line.split("::");
				String url = elements[0];
				String imageUrl = elements[1];
				ret.put(url, imageUrl);
			}
			return ImmutableMap.copyOf(ret);
		}

		private static List<String> filterByCategory(List<String> lines,
				final String categoryId) {
			return FluentIterable.from(lines).filter(new Predicate<String>() {
				@Override
				public boolean apply(@Nullable String input) {
					return input.startsWith(categoryId);
				}
			}).toList();
		}

		private static Collection<String> getChildCategories(String iRootId) {
		System.out.println("getChildCategories() - begin");	
			File file = Paths.get("/home/sarnobat/github/yurl/tmp/categories/topology/" + iRootId + ".txt").toFile();
			if (!file.exists()) {
				java.nio.file.Path p = Paths.get("/home/sarnobat/github/yurl/yurl_category_topology.txt.2017-07-29.columns_reordered");
				List<String> childCategories = new LinkedList<String>();
				StringBuffer sb = new StringBuffer();
				for (String line : FileUtils.readLines(p.toFile(), "UTF-8")) {
					String[] elements = line.split("::");
//					System.out
//							.println("YurlList.YurlResource.getChildCategories() 1 " + line);
					if (elements.length < 2) {
						continue;
					}
//					System.out
//					.println("YurlList.YurlResource.getChildCategories() 2 " + line);
					String categoryId = elements[0];
					String childCategoryId = elements[1];
					if (categoryId.equals(iRootId)) {
						childCategories.add(childCategoryId);
						sb.append(childCategoryId);
						sb.append("\n");
					}
				}
				FileUtils.writeLines(file, "UTF-8", childCategories);
			}
			System.out.println("YurlList.YurlResource.getChildCategories() file = " + file.getPath());
			List<String> categoryLines = FileUtils.readLines(file, "UTF-8");
			return ImmutableList.copyOf(categoryLines);
		}

		private static List<String> filter(List<String> categoryLines, final String iRootId) {
			return FluentIterable.from(categoryLines).filter(new Predicate<String>(){
				@Override
				public boolean apply(@Nullable String input) {
					return input.startsWith(iRootId);
				}}).toList();
		}

		////
		//// The main part
		////
		// TODO: See if you can turn this into a map-reduce
		@SuppressWarnings("unused")
		@Deprecated
		private static JSONObject getItemsAtLevelAndChildLevelsNeo4j(Integer iRootId) throws JSONException, IOException {
//			System.out.println("getItemsAtLevelAndChildLevels() - " + iRootId);
			if (categoriesTreeCache == null) {
				categoriesTreeCache = ReadCategoryTree.getCategoriesTreeNeo4j(YurlList.ROOT_ID);
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
								System.out.println("YurlWorldResource.getItemsAtLevelAndChildLevels() - does this ever occur? 1");
							}
							anUncategorizedNodeJsonObject.put("biggest_image", aValue);
						}
					}
					_18: {
						Object val = anItem.get(9);
						if (isNotNull(val)) {
							String aValue = (String) val;
							if ("null".equals(aValue)) {
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
			return val != null && !("null".equals(val)) && !(val.getClass().equals(YurlResource.JSON_OBJECT_NULL));
		}

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------

		private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

		static JSONObject execute(String iCypherQuery, Map<String, Object> iParams, String... iCommentPrefix) {
			execute(iCypherQuery, iParams, true, iCommentPrefix);
		}

		// TODO: make this map immutable
		@Deprecated
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
		
		@Deprecated
		private static void refreshCategoriesTreeCacheInSeparateThread() {
			new Thread(){
				@Override
				public void run() {
					try {
						categoriesTreeCache = ReadCategoryTree.getCategoriesTreeNeo4j(YurlList.ROOT_ID);
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
		

		private static class ReadCategoryTreeNonNeo4j {
			static JSONObject getCategoriesTreeNeo4j(Integer rootId) {

				java.nio.file.Path namesFile = Paths.get(CATEGORY_NAMES);
				Map<Integer,String> names = new HashMap<Integer, String>();
				for (String line : FileUtils.readLines(namesFile.toFile(), "UTF-8")) {
					String[] elements = line.split("::");
					Integer i = Integer.parseInt(elements[0]);
					String name = elements[1];
					names.put(i, name);
				}

				java.nio.file.Path relationships = Paths.get(CATEGORY_RELATIONSHIPS);
				Map<Integer, Integer> parents = new HashMap<Integer, Integer>();
				for (String line : FileUtils.readLines(relationships.toFile(), "UTF-8")) {
					String[] elements = line.split("::");
					Integer child = Integer.parseInt( elements[0]);
					Integer parent = Integer.parseInt(elements[1]);
					parents.put(child, parent);
				}
				
				
			}
		}

		@Deprecated
		private static class ReadCategoryTree {
			@Deprecated
			static JSONObject getCategoriesTreeNeo4j(Integer rootId)
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

		@Deprecated
		private static class MongoDbCache {


			public static final boolean ENABLED = false;
			private static final String HOST = "192.168.1.2";
			private static final int PORT = 27017;

			private static final String VALUE = "value";
			private static final String ID = "_id";

			private static final String COLLECTION = "items";
			private static final String CACHE = "cache";

			private static boolean delete(String key) {
				MongoClient mongo;
				try {
					mongo = new MongoClient(HOST, PORT);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
				DB db = mongo.getDB(CACHE);
				DBCollection table = db.getCollection(COLLECTION);
				BasicDBObject searchQuery = new BasicDBObject(ID, key);
				return table.remove(searchQuery).getN() > 0;
			}

			static boolean invalidate(String key) {
				delete(key);
			}

			static boolean exists(String key) {
				MongoClient mongo;
				try {
					mongo = new MongoClient(HOST, PORT);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
				DB db = mongo.getDB(CACHE);
				DBCollection table = db.getCollection(COLLECTION);
				BasicDBObject searchQuery = new BasicDBObject(ID, key);
				return table.find(searchQuery).size() > 0;
			}

			static void put(String key, String value) {
				MongoClient mongo;
				try {
					mongo = new MongoClient(HOST, PORT);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
				DB db = mongo.getDB(CACHE);
				DBCollection collection = db.getCollection(COLLECTION);
				BasicDBObject document = new BasicDBObject(ID, key);
				document.put(VALUE, value);
				collection.insert(document);
			}

			static String get(String key) {
				MongoClient mongo;
				try {
					mongo = new MongoClient(HOST, PORT);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
				DB db = mongo.getDB(CACHE);
				DBCollection collection = db.getCollection(COLLECTION);
				BasicDBObject searchQuery = new BasicDBObject(ID, key);
				DBCursor cursor = collection.find(searchQuery);
				DBObject next = cursor.next();
				return (String) next.get(VALUE);
			}
		}
	}

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {

		YurlResource.refreshCategoriesTreeCacheInSeparateThread();
		// Turn off that stupid Jersey logger.
		// This works in Java but not in Groovy.
		//java.util.Logger.getLogger("org.glassfish.jersey").setLevel(java.util.Level.SEVERE);
		try {
			JdkHttpServerFactory.createHttpServer(
					new URI("http://localhost:4443/"), new ResourceConfig(
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
