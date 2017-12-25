import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SimpleTimeLimiter;
//import org.jvnet.hk2.annotations.Optional;

/**
 * Deprecation doesn't mean the method can be removed. Only when index.html stops referring to it can it be removed.
 * 
 * Neo4j dependencies removed.
 * 
 * Only writing to the persistent store (and the associated async tasks) should remain in this thread.
 */
// TODO: rename to YurlStash
public class YurlStash {

	public static final String YOUTUBE_DOWNLOAD = "/home/sarnobat/bin/youtube_download";
	public static final Integer ROOT_ID = 45;
	@Deprecated
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	private static final String TARGET_DIR_PATH = "/media/sarnobat/Unsorted/Videos/";
	@Deprecated
	private static final String QUEUE_FILE = "/home/sarnobat/sarnobat.git/";
	private static final String QUEUE_FILE_TXT = "yurl_queue.txt";
	private static final String TITLE_FILE_TXT = "yurl_titles_2017.txt";
    private static final String QUEUE_FILE_TXT_MASTER = "yurl_master.txt";
    private static final String QUEUE_FILE_TXT_2017 = "yurl_queue_2017.txt";// Started using this in Aug 2017. Older data is not in this file.
    private static final String QUEUE_DIR = "/home/sarnobat/sarnobat.git/db/yurl_flatfile_db/";
	private static final String QUEUE_FILE_TXT_DELETE = "yurl_deleted.txt";
	private static final String TARGET_DIR_PATH_IMAGES = "/media/sarnobat/3TB/new/move_to_unsorted/images/";
// usually full and we get zero size files: "/media/sarnobat/Unsorted/images/";
	private static final String TARGET_DIR_PATH_IMAGES_OTHER = "/media/sarnobat/3TB/new/move_to_unsorted/images/other";
// usually full and images don't get saved "/media/sarnobat/Unsorted/images/other";
	
	@Path("yurl")
	public static class YurlResource { // Must be public


		static {
			// We can't put this in the constructor because multiple instances will get created
			// YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		}

		private static JSONObject categoriesTreeCache;

		// This only gets invoked when it receives the first request
		// Multiple instances get created
		public YurlResource() {
			// We can't put the auto downloader in main()
			// then either it will be called every time the cron job is executed,
			// or not until the server terminates unexceptionally (which never happens).
		}
		
		@GET
		@Path("downloadVideo")
		@Produces("application/json")
		public Response downloadVideoSynchronous(@QueryParam("id") Integer iRootId, @QueryParam("url") String iUrl)
				throws JSONException, IOException {
			DownloadVideo.getVideoDownloadJobHttpcat(iUrl, TARGET_DIR_PATH).run();
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
			StringBuffer unsuccessfulLines = new StringBuffer();

			String[] lines = iUrls.trim().split("\\n");
			int i = 0;
			while (i < lines.length) {

				String first = lines[i];
				try {
					if (first.startsWith("http")) {
						stash(URLEncoder.encode(first, "UTF-8"), iRootId);
						++i;
						continue;
					}
					// Fails if it sees the string "%)"
					URLDecoder.decode(first, "UTF-8");
				} catch (Exception e) {
					System.out.println(e.getStackTrace());
					addToUnsuccessful(unsuccessfulLines, first, "");
					++i;
					continue;
				}
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
				if (first.matches("^\\s*" + '$')) {
					++i;
					continue;
				}

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
		@Deprecated
		public void addToUnsuccessful(StringBuffer unsuccessfulLines,
				String first, String second) {
			unsuccessfulLines.append(first);
			unsuccessfulLines.append("\n");
			unsuccessfulLines.append(second);
			unsuccessfulLines.append("\n");
			unsuccessfulLines.append("\n");
		}

		// ------------------------------------------------------------------------------------
		// Page operations
		// ------------------------------------------------------------------------------------

		////
		//// The main part
		////
		// TODO: See if you can turn this into a map-reduce
		@SuppressWarnings("unused")
		private JSONObject getItemsAtLevelAndChildLevels(Integer iRootId) throws JSONException, IOException {
//			System.out.println("getItemsAtLevelAndChildLevels() - " + iRootId);
			if (categoriesTreeCache == null) {
				categoriesTreeCache = ReadCategoryTree.getCategoriesTree(YurlStash.ROOT_ID);
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

		// -----------------------------------------------------------------------------
		// Key bindings
		// -----------------------------------------------------------------------------

		
		


		

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------

		@GET
		@Path("stash")
		@Produces("application/json")
		public Response stash(@QueryParam("param1") String iUrl,
				@QueryParam("rootId") Integer iCategoryId) throws JSONException,
				IOException {
			// This will convert
			String theHttpUrl = URLDecoder.decode(iUrl, "UTF-8");
			System.out.println("stash() theHttpUrl = " + theHttpUrl);
			try {
				// TODO: append synchronously to new yurl master queue 
	            appendToTextFileSync(iUrl, iCategoryId.toString(), QUEUE_FILE, YurlStash.QUEUE_FILE_TXT_MASTER);

				launchAsynchronousTasksHttpcat(theHttpUrl, iCategoryId);
				// TODO: check that it returned successfully (redundant?)
//				System.out.println("stash() - node created: " + nodeId);
				System.out.println("YurlStash.YurlResource.stash() sending empty json response. This should work.");
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(new JSONObject().toString())
						.type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				throw new JSONException(e);
			}
		}


        private static void launchAsynchronousTasksHttpcat(final String iUrl, Integer iCategoryId) throws IOException, InterruptedException {
		
			appendToTextFileSync(iUrl, iCategoryId.toString(), QUEUE_DIR, YurlStash.QUEUE_FILE_TXT_2017);
			
			// Delete the url cache file for this category. It will get
			// regenrated next time we load that category page.
        	removeCategoryCache(iCategoryId);
        	
        	// Get the title
			
			_10: {

				final String theTitle = getTitle(new URL(iUrl));
				if (theTitle != null && theTitle.length() > 0) {
					Runnable r = new Runnable() {
						// @Override
						public void run() {
							String titleFileStr = YurlStash.QUEUE_FILE + "/" + YurlStash.TITLE_FILE_TXT;
							File file = Paths.get(titleFileStr).toFile();
							File file2 = Paths.get(YurlStash.QUEUE_FILE).toFile();
							if (!file2.exists()) {
								throw new RuntimeException("Non-existent: " + file.getAbsolutePath());
							}
							String command = "echo '" + iUrl + "::" + theTitle + "' | tee -a '" + titleFileStr + "'";
							System.out.println("appendToTextFile() - " + command);
							Process p;
							try {
								p = new ProcessBuilder()
										.directory(file2)
										.command("echo","hello world")
										.command("/bin/sh", "-c", command)
										.inheritIO().start();
								try {
									p.waitFor();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								if (p.exitValue() == 0) {
									System.out.println("appendToTextFile() - successfully appended 2 "
											+ iUrl);
								} else {
									System.out.println("appendToTextFile() - error appending " + iUrl);
								}
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					};
					new Thread(r).start();
				}
			}
        	
            // This is not (yet) the master file. The master file is written to synchronously.
            appendToTextFile(iUrl, iCategoryId.toString(), QUEUE_FILE);
            String targetDirPathImages;
            if (iCategoryId.longValue() == 29172) {
                    targetDirPathImages = TARGET_DIR_PATH_IMAGES_OTHER;
            } else {
                    targetDirPathImages = TARGET_DIR_PATH_IMAGES;
            }
            DownloadImage.downloadImageInSeparateThreadHttpcat(iUrl, targetDirPathImages, CYPHER_URI);
            DownloadVideo.downloadVideoInSeparateThreadHttpcat(iUrl, TARGET_DIR_PATH, CYPHER_URI);
            if (!iUrl.contains("amazon")) {
                    // We end up with garbage images if we try to screen-scrape Amazon.
                    // The static rules result in better images.
            		// TODO: Store this successful image download outside Neo4j.
                    //BiggestImage.recordBiggestImage(iUrl, CYPHER_URI, id);
            }
        }

		private static void removeCategoryCache(Integer iCategoryId) {
			java.nio.file.Path path1 = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/urls/" + iCategoryId + ".json");
			path1.toFile().delete();
        	System.out.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 1: " + path1);
        	
        	java.nio.file.Path path = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/categories/topology/" + iCategoryId + ".txt");
			path.toFile().delete();
        	System.out.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 2: " + path);
		}

		private static void appendToTextFileSync(final String iUrl,
				final String id, final String dir, String file2, long created)
				throws IOException, InterruptedException {

			String queueFile = dir + "/" + file2;
			File file = Paths.get(dir).toFile();
			if (!file.exists()) {
			    throw new RuntimeException("Non-existent: " + file.getAbsolutePath());
			}
			String command =  "echo '" + id + "::" + iUrl + "::"+created+"' | tee -a '" + queueFile + "'";
			System.out.println("appendToTextFileSync() - " + command);
			Process p = new ProcessBuilder()
			            .directory(file)
			            .command("echo","hello world")
			            .command("/bin/sh", "-c", command)
			                            //"touch '" + queueFile + "'; echo '" + id + ":" + iUrl + "' >> '" + queueFile + "'"
			                                            .inheritIO().start();
			p.waitFor();
			if (p.exitValue() == 0) {
			    System.out.println("appendToTextFileSync() - successfully appended 3 "
			                    + iUrl);
			} else {
			    System.out.println("appendToTextFileSync() - error appending " + iUrl);
			}
		}
		
        private static void appendToTextFileSync(final String iUrl, final String id, final String dir, String file2) throws IOException,
                        InterruptedException {
            String queueFile = dir + "/" + file2;
            File file = Paths.get(dir).toFile();
            if (!file.exists()) {
                    throw new RuntimeException("Non-existent: " + file.getAbsolutePath());
            }
            String command =  "echo '" + id + "::" + iUrl + "::'`date +%s` | tee -a '" + queueFile + "'";
            System.out.println("appendToTextFileSync() - " + command);
            Process p = new ProcessBuilder()
                            .directory(file)
                            .command("echo","hello world")
                            .command("/bin/sh", "-c", command)
                                            //"touch '" + queueFile + "'; echo '" + id + ":" + iUrl + "' >> '" + queueFile + "'"
                                                            .inheritIO().start();
            p.waitFor();
            if (p.exitValue() == 0) {
                    System.out.println("appendToTextFileSync() - successfully appended 4 "
                                    + iUrl);
            } else {
                    System.out.println("appendToTextFileSync() - error appending " + iUrl);
            }
        }

		private static void appendToTextFile(final String iUrl, final String id, final String dir) throws IOException,
				InterruptedException {
			Runnable r = new Runnable() {
				// @Override
				public void run() {
					String queueFile = dir + "/" + YurlStash.QUEUE_FILE_TXT;
					File file = Paths.get(dir).toFile();
					if (!file.exists()) {
						throw new RuntimeException("Non-existent: " + file.getAbsolutePath());
					}
					String command =  "echo '" + id + "::" + iUrl + "::'`date +%s` | tee -a '" + queueFile + "'";
					System.out.println("appendToTextFile() - " + command);
					Process p;
					try {
						p = new ProcessBuilder()
								.directory(file)
								.command("echo","hello world")
								.command("/bin/sh", "-c", command)
										//"touch '" + queueFile + "'; echo '" + id + ":" + iUrl + "' >> '" + queueFile + "'"
												.inheritIO().start();
						try {
							p.waitFor();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (p.exitValue() == 0) {
							System.out.println("appendToTextFile() - successfully appended 5 "
									+ iUrl);
						} else {
							System.out.println("appendToTextFile() - error appending " + iUrl);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			new Thread(r).start();
		}
		
		private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

		private static class DownloadImage {
			private static void downloadImageInSeparateThreadHttpcat(final String iUrl2,
                                        final String targetDirPath, final String cypherUri) {
                                final String iUrl = iUrl2.replaceAll("\\?.*", "");
                                Runnable r = new Runnable() {
                                        // @Override
                                        public void run() {
                                                System.out.println("downloadImageInSeparateThreadHttpcat() - " + iUrl + " :: " + targetDirPath);
                                                if (iUrl.toLowerCase().contains(".jpg")) {
                                                } else if (iUrl.toLowerCase().contains(".jpeg")) {
                                                } else if (iUrl.toLowerCase().contains(".png")) {
                                                } else if (iUrl.toLowerCase().contains(".gif")) {
                                                } else if (iUrl.toLowerCase().contains("gstatic")) {
                                                } else {
                                                        return;
                                                }
                                                System.out
                                                                .println("Yurl.YurlResource.DownloadImage.downloadImageInSeparateThreadHttpcat() Is of image type");
                                                try {
                                                        System.out.println("Yurl.YurlResource.DownloadImage.downloadImageInSeparateThreadHttpcat() About to call saveimage");
                                                        saveImage(iUrl, targetDirPath);
                                                        System.out.println("Yurl.YurlResource.DownloadImage.downloadImageInSeparateThreadHttpcat() About to call execute: TODO - record the fact that we downloaded the image somwhere");
                                                        System.out.println("YurlWorldResource.downloadImageInSeparateThreadHttpcat() - image download recorded");
                                                } catch (Exception e) {
                                                        System.out.println("YurlWorldResource.downloadImageInSeparateThreadHttpcat(): 1 Biggest image couldn't be determined"    + e.getMessage());
                                                }
                                        }
                                };
                                new Thread(r).start();
                        }

			private static void saveImage(String urlString, String targetDirPath)
					throws IllegalAccessError, IOException {
				System.out.println("saveImage() - " + urlString + "\t::\t" + targetDirPath);
				String extension = FilenameUtils.getExtension(urlString);
				ImageIO.write(
						ImageIO.read(new URL(urlString)),
						extension,
						new File(determineDestinationPathAvoidingExisting(
								targetDirPath										+ "/"										+ URLDecoder.decode(FilenameUtils.getBaseName(urlString)
												.replaceAll("/", "-"), "UTF-8") + "." + extension)
								.toString()));
				System.out.println("saveImage() - SUCCESS: " + urlString + "\t::\t" + targetDirPath);
			}

			private static java.nio.file.Path determineDestinationPathAvoidingExisting(
					String iDestinationFilePath) throws IllegalAccessError {
				String theDestinationFilePathWithoutExtension = iDestinationFilePath.substring(0,
						iDestinationFilePath.lastIndexOf('.'));
				String extension = FilenameUtils.getExtension(iDestinationFilePath);
				java.nio.file.Path oDestinationFile = Paths.get(iDestinationFilePath);
				while (Files.exists(oDestinationFile)) {
					theDestinationFilePathWithoutExtension += "1";
					iDestinationFilePath = theDestinationFilePathWithoutExtension + "." + extension;
					oDestinationFile = Paths.get(iDestinationFilePath);
				}
				if (Files.exists(oDestinationFile)) {
					throw new IllegalAccessError("an existing file will get overwritten");
				}
				return oDestinationFile;
			}
		}

		private static String getTitle(final URL iUrl) {
			String title = "";
			try {
				title = Executors
						.newFixedThreadPool(2)
						.invokeAll(
								ImmutableSet.<Callable<String>> of(new Callable<String>() {
									public String call() throws Exception {
										try {
											return Jsoup.connect(iUrl.toString()).get().title();
										} catch (org.jsoup.UnsupportedMimeTypeException e) {
											System.out.println("YurlResource.getTitle() - " + e.getMessage());
											return "";
										}
									}
								}), 3000L, TimeUnit.SECONDS).get(0).get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			return title;
		}

		private static class DownloadVideo {

	        static void downloadVideoInSeparateThreadHttpcat(String iVideoUrl,
	                        String TARGET_DIR_PATH, String cypherUri) {
	                System.out.println("YurlWorldResource.downloadVideoInSeparateThreadHttpcat() - begin: "
	                                + iVideoUrl);
	                // VGet stopped working, so now we use a shell callout
	                Runnable r2 = getVideoDownloadJobHttpcat(iVideoUrl, TARGET_DIR_PATH);
	                executorService.submit(r2);
	        }

	        static Runnable getVideoDownloadJobHttpcat(final String iVideoUrl, final String targetDirPath
                                       ) {
                                Runnable videoDownloadJob = new Runnable() {
                                        @Override
                                        public void run() {
                                                try {
                                                        Process p = new ProcessBuilder()
                                                                        .directory(Paths.get(targetDirPath).toFile())
                                                                        .command(
                                                                                        ImmutableList.of(YurlStash.YOUTUBE_DOWNLOAD,
                                                                                                        iVideoUrl)).inheritIO().start();
                                                        p.waitFor();
                                                        if (p.exitValue() == 0) {
                                                                System.out
                                                                                .println("YurlWorldResource.downloadVideoInSeparateThreadHttpcat() - successfully downloaded "
                                                                                                + iVideoUrl);
								// TODO : write successful video download to file
                                                                //writeSuccessToDb(iVideoUrl, id);
                                                        } else {
                                                                System.out
                                                                                .println("YurlWorldResource.downloadVideoInSeparateThreadHttpcat() - error downloading "
                                                                                                + iVideoUrl);
                                                        }
                                                } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                } catch (IOException e) {
                                                        e.printStackTrace();
                                                }
                                        }
                                };
                                return videoDownloadJob;
                        }

		}

		@SuppressWarnings("unused")
		private static class DownloadVideosBatch {
			@SuppressWarnings("unused")
			private static void downloadUndownloadedVideosInSeparateThread() {
				new Thread() {
					public void run() {
						try {
							downloadUndownloadedVideosBatch();

							// I don't remember what this is for
							while (true) {
								try {
									Thread.sleep(1000 * 60 * 60);
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

			private static void downloadUndownloadedVideosBatch() throws JSONException, IOException {
				String query = "start root=node(37658)" + " match  root-[CONTAINS*]->n"
						+ " where not(has(n.downloaded_video)) OR n.downloaded_video is null "
						+ " return n.title, n.downloaded_video, n.url, ID(n)" + " LIMIT 20;";
				JSONObject results = execute(query, ImmutableMap.<String, Object> of(),
						"downloadUndownloadedVideosBatch()");
				JSONArray jsonArray = results.getJSONArray("data");
				for (int i = 0; i < jsonArray.length(); i++) {
					final JSONArray row = jsonArray.getJSONArray(i);
					System.out.println("downloadUndownloadedVideosBatch() - iteration: " + row);
					Object title = row.get(0);
					Object downloaded = row.get(1);
					Object url = row.get(2);
					Object id = row.get(3);
					if (downloaded == null || downloaded == JSONObject.NULL
							|| "null".equals(downloaded)) {
						if (url instanceof String) {
							if (id instanceof Integer) {
								System.out
										.println("downloadUndownloadedVideosBatch() - Starting retroactively downloading: "
												+ title);
								try {
									new SimpleTimeLimiter().callWithTimeout(new Callable<Object>() {

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
								System.out
										.println("downloadUndownloadedVideosBatch() - Finished retroactively downloading: "
												+ title);
							} else {
								System.out.println("id - " + id.getClass());
							}
						} else {
							System.out.println("url - " + url.getClass());
						}
					} else {
						Long downloaded1 = row.getLong(1);
						System.out
								.println("downloadUndownloadedVideosBatch() - Already downloaded: "
										+ title + "\t" + downloaded1);
					}
				}
			}

			private static void downloadVideo(String iVideoUrl, String targetDirPath, String id) {
				try {
//					downloadVideo(iVideoUrl, targetDirPath);
//					writeSuccessToDb(iVideoUrl, id);
				} catch (JSONException e) {
					System.out.println("UndownloadedVideosBatchJob.downloadVideo() - ERROR recording download in database");
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}

		}

		private static void writeSuccessToDb(final String iVideoUrl, final String id)
				throws IOException {
                        System.out.println("writeSuccessToDb() - Attempting to record successful download in database...");
			execute("start n=node({id}) WHERE n.url = {url} SET n.downloaded_video = {date}",
					ImmutableMap.<String, Object> of("id", Long.valueOf(id), "url", iVideoUrl,
							"date", System.currentTimeMillis()), "downloadVideo()");
			System.out.println("writeSuccessToDb() - Download recorded in database");
		}

		@GET
		@Path("updateImage")
		@Produces("application/json")
		public Response changeImage(
				@QueryParam("url") String imageUrl,
				@QueryParam("linkUrl") String iUrl,
				@QueryParam("categoryId") String iCategoryId)
				throws IOException, JSONException {
			System.err.println("Yurl.YurlResource.changeImage() begin");
			
			FileUtils.write(Paths.get(System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_master_images.txt").toFile(), iUrl + "::" + imageUrl + "\n", "UTF-8", true);
			System.err.println("Yurl.YurlResource.changeImage() - " + iUrl + " :: " + imageUrl);

			removeCategoryCacheAsync(iCategoryId);
			// TODO: remove the Neo4j part
//			JSONObject execute = execute(
//					"START n=node({nodeIdToChange}) "
//							+ "SET n.user_image =  {imageUrl}"
//							+ "RETURN n",
//					ImmutableMap.<String, Object> of("nodeIdToChange",
//							nodeIdToChange, "imageUrl",
//							imageUrl), "changeImage()");
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject()).type("application/json")
					.build();
		}

		private void removeCategoryCacheAsync(final String iCategoryId) {
			new Thread() {
				@Override
				public void run() {
					removeCategoryCache(Integer.parseInt(iCategoryId));
				}
			}.start();
		}

		// I don't think this is of any use anymore
		@GET
		@Path("removeImage")
		@Produces("application/json")
		public Response removeImage(
				@QueryParam("id") Integer nodeIdToChange,
				@QueryParam("parentId") Integer parentId) throws Exception {
	
			System.out.println("removeImage() - begin");
			try {
				Response r = Response
						.ok()
						.header("Access-Control-Allow-Origin", "*")
						.entity(execute("START n=node({nodeIdToChange}) "
								+ "REMOVE n.user_image, n.biggest_image " + "RETURN n",
								ImmutableMap.<String, Object> of("nodeIdToChange", nodeIdToChange),
								"removeImage()")).type("application/json").build();
				if (parentId == null) {
					System.err.println("YurlWorldResource.removeImage() - Warning: cache not updated, because parentId was not passed");
				} else {
				}
				return r;
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
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

		@Deprecated // Uses Neo4j
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
		 * @throws InterruptedException 
		 */
		@GET
		@Path("relate")
		@Produces("application/json")
		public Response move(@QueryParam("parentId") final Integer iNewParentId,
				@QueryParam("url") String iUrl,
				@QueryParam("currentParentId") final Integer iCurrentParentId,
				@QueryParam("created") Long created)
				throws JSONException, IOException, InterruptedException {
			
			System.out.println("Yurl.YurlResource.move() begin");
			
			appendToTextFileSync(iUrl, iNewParentId.toString(), QUEUE_FILE, YurlStash.QUEUE_FILE_TXT_MASTER, created);
			
			System.out.println("Yurl.YurlResource.move() 2");
			appendToTextFileSync(iUrl, "-" + iCurrentParentId.toString(), QUEUE_DIR, YurlStash.QUEUE_FILE_TXT_DELETE, created);
			System.out.println("Yurl.YurlResource.move() 4");
			
			new Thread() {
				@Override
				public void run() {
					removeCategoryCache(iNewParentId);
				}
			}.start();
			new Thread() {
				@Override
				public void run() {
					removeCategoryCache(iCurrentParentId);
				}
			}.start();
			
//			JSONObject moveHelper = relateToExistingCategory(iChildId, iCurrentParentId,
//					iNewParentId);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject())
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

		@Deprecated
		static JSONObject execute(String iCypherQuery, Map<String, Object> iParams, String... iCommentPrefix) {
			return execute(iCypherQuery, iParams, true, iCommentPrefix);
		}

		// TODO: Only delete this once we're no longer using the 2015 backup of the neo4j DB. It is still providing some functinoality.
		@Deprecated
		// TODO: make this map immutable
		static JSONObject execute(String iCypherQuery,
				Map<String, Object> iParams, boolean doLogging, String... iCommentPrefix) {
			throw new RuntimeException("Do not use this method");
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
			java.nio.file.Path path = Paths.get("/home/sarnobat/github/.cache/" + YurlStash.ROOT_ID + ".json");
			if (Files.exists(path)) {
				categoriesTreeJson = new JSONObject(FileUtils.readFileToString(path.toFile(), "UTF-8"));
			} else {
				if (categoriesTreeCache == null) {
					categoriesTreeJson = ReadCategoryTree.getCategoriesTree(YurlStash.ROOT_ID);
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
						//categoriesTreeCache = ReadCategoryTree.getCategoriesTree(Yurl.ROOT_ID);
					} catch (JSONException e) {
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
		public static final Object JSON_OBJECT_NULL = JSONObject.NULL;//new Null()
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
			port = cmd.getOptionValue("p", "4447");

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
							YurlStash.YurlResource.class));
			// Do not allow this in multiple processes otherwise your hard disk will fill up
			// or overload the database
			// Problem - this won't get executed until the server ends
			//YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		} catch (Exception e) {
//	e.printStackTrace();
			System.out.println("Not creating server instance");
		}
	}
}
