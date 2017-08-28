import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

// TODO: Use javax.json.* for immutability
public class YurlList {

	// Gets stored here: http://192.168.1.2:28017/cache/items/
	@Deprecated
	public static final Integer ROOT_ID = 45;
	@Deprecated
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";

	private static final String YURL_ORDINALS = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_master_ordinals.txt";
	private static final String DOWNLOADED_VIDEOS = System.getProperty("user.home") + "/sarnobat.git/db/auto/yurl_queue_httpcat_videos_downloaded.json";
	private static final String DOWNLOADED_VIDEOS_2017 = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/videos_download_succeeded.txt";
    private static final String QUEUE_DIR = "/home/sarnobat/sarnobat.git/db/yurl_flatfile_db/";
	private static final String QUEUE_FILE_TXT_DELETE = "yurl_deleted.txt";

	// TODO: Regenerate the cache file using these sources of truth.
	private static final String CATEGORY_HIERARCHY_JSON = System.getProperty("user.home") +"/github/yurl/cache/categories/all.json";
	private static final String CATEGORY_RELATIONSHIPS = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_category_topology.txt";
	private static final String CATEGORY_NAMES = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_category_names.txt";
	
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
		
		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response getUrls(@QueryParam("rootId") Integer iRootId,
								@QueryParam("enableCache") @DefaultValue("true") Boolean iMongoDbCacheLookupEnabled)
				throws JSONException, IOException {
			checkNotNull(iRootId);
			JSONObject categoriesTreeJson;
			
			if (Paths.get(CATEGORY_HIERARCHY_JSON).toFile().exists()) {
				System.out.println("getUrls() - preloaded categories tree not ready");
				categoriesTreeJson = readFromCache(CATEGORY_HIERARCHY_JSON);
			} else {
				throw new RuntimeException("does not exist: " + CATEGORY_HIERARCHY_JSON);
			}
			try {
				JSONObject oUrlsUnderCategory;
				// We're not getting up to date pages when things change. But we need to
				// start using this again if we dream of scaling this app.
					// If there were multiple clients here, you'd need to block the 2nd onwards
					System.out.println("YurlWorldResource.getUrls() - not using cache");
					JSONObject retVal1;
					retVal1 = new JSONObject();
					// TODO: Remove this
					//retVal1.put("urlsNeo4j", getItemsAtLevelAndChildLevelsNeo4j(iRootId));
					
					Collection<String> downloadedVideos = new HashSet(getDownloadedVideos(DOWNLOADED_VIDEOS));
					downloadedVideos.addAll(getDownloadedVideos2017(DOWNLOADED_VIDEOS_2017));
					retVal1.put("urls", getItemsAtLevelAndChildLevels(iRootId, downloadedVideos));
					// We do need this. Ideally
					// server_categoriesRecursive.groovy would do it but at the
					// moment that only uses neo4j.
					retVal1.put("categoriesRecursive", categoriesTreeJson);
					oUrlsUnderCategory = retVal1;
				
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

		private JSONObject readFromCache(String filePath) throws IOException {
			System.out.println("YurlList.YurlResource.readFromCache() " + filePath);
			File f = Paths.get(filePath).toFile();
			String s = FileUtils.readFileToString(f, "UTF-8");
			System.out.println("YurlList.YurlResource.readFromCache() success");
			return new JSONObject(s);
		}

		@Deprecated // TODO: this file is not in the right format 
		private Collection<String> getDownloadedVideos(String downloadedVideos) throws IOException {
			return FileUtils.readLines(Paths.get(downloadedVideos).toFile(), "UTF-8");
		}
		
		private Collection<String> getDownloadedVideos2017(String downloadedVideos) throws IOException {
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
				Integer iRootId) throws IOException {

			List<String> lines = FileUtils.readLines(Paths.get(yurlOrdinals).toFile(), "UTF-8");
			Map<String, String> ret = new HashMap<String, String>();
			for (String line : filter(lines, iRootId.toString())) {
				String[] elements = line.split("::");
				ret.put(elements[1], elements[2]);
			}
			
			return ImmutableMap.copyOf(ret);
		}

		private static JSONArray getUrlsInCategory(String categoryId, Map<String, String> orderMap, Collection<String> downloadedVideos) throws IOException {
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

		private static Map<String, String> getUserImages(java.nio.file.Path path) throws IOException {

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

		private static Collection<String> getChildCategories(String iRootId) throws IOException {
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

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------

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

		/**
		 * file will get written to
		 */
		// TODO: implement this
		private static Thread refreshCategoriesTreeCacheInSeparateThreadNoNeo4j() {
			return new Thread(){
				@Override
				public void run() {
					System.out
							.println("YurlList.YurlResource.refreshCategoriesTreeCacheInSeparateThreadNoNeo4j() UNIMPLEMENTED");
				}
			};
		}
	}

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {

		YurlResource.refreshCategoriesTreeCacheInSeparateThreadNoNeo4j().start();
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
