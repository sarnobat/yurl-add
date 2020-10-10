// I think the only remaining method in use here is:
// Request URL: http://netgear.rohidekar.com:44447/yurl/parent?nodeId=641476
// Everything else doesn't use port 4447
// Actually also image change is

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.google.common.collect.ImmutableSet;

/**
 * Deprecation doesn't mean the method can be removed. Only when index.html stops referring to it can it be removed.
 * 
 * Neo4j dependencies removed.
 * 
 * Only writing to the persistent store (and the associated async tasks) should remain in this thread.
 */
// TODO: rename to YurlStash
public class YurlStash {

	// TODO: use java properties
	public static final Integer ROOT_ID = 45;
    private static final String QUEUE_DIR = "/home/sarnobat/sarnobat.git/db/yurl_flatfile_db/";
	private static final String QUEUE_FILE = QUEUE_DIR;
	private static final String QUEUE_FILE_TXT = "yurl_queue.txt";
	private static final String TITLE_FILE_TXT = "yurl_titles_2017.txt";
    private static final String QUEUE_FILE_TXT_MASTER = "yurl_master.txt";
    private static final String QUEUE_FILE_TXT_2017 = "yurl_queue_2017.txt";// Started using this in Aug 2017. Older data is not in this file.
	private static final String QUEUE_FILE_TXT_DELETE = "yurl_deleted.txt";
	
	@Path("yurl")
	public static class YurlResource { // Must be public

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------

		@GET
		@Path("stash")
		@Produces("application/json")
		public Response stash(@QueryParam("param1") String iUrl,
				@QueryParam("rootId") Integer iCategoryId) throws JSONException,
				IOException {

			System.err.println("stash() begin");
			String theHttpUrl = URLDecoder.decode(iUrl, "UTF-8");
			System.err.println("stash() theHttpUrl = " + theHttpUrl);
			try {
				// TODO: append synchronously to new yurl master queue 
	            appendToTextFileSync(iUrl, iCategoryId.toString(), QUEUE_FILE, YurlStash.QUEUE_FILE_TXT_MASTER);

				launchAsynchronousTasksHttpcat(theHttpUrl, iCategoryId);
				System.err.println("YurlStash.YurlResource.stash() sending empty json response. This should work.");
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
        	
			_getTitle: {

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
							System.err.println("appendToTextFile() - " + command);
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
									System.err.println("appendToTextFile() - successfully appended 2 "
											+ iUrl);
								} else {
									System.err.println("appendToTextFile() - error appending " + iUrl);
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
        }

		private static void removeCategoryCache(Integer iCategoryId) {
			java.nio.file.Path path1 = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/urls/" + iCategoryId + ".json");
			path1.toFile().delete();
        	System.err.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 1: " + path1);
        	
        	java.nio.file.Path path = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/categories/topology/" + iCategoryId + ".txt");
			path.toFile().delete();
        	System.err.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 2: " + path);
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
			System.err.println("appendToTextFileSync() - " + command);
			Process p = new ProcessBuilder()
			            .directory(file)
			            .command("echo","hello world")
			            .command("/bin/sh", "-c", command)
			            .inheritIO().start();
			p.waitFor();
			if (p.exitValue() == 0) {
			    System.err.println("appendToTextFileSync() - successfully appended 3 "
			                    + iUrl);
			} else {
			    System.err.println("appendToTextFileSync() - error appending " + iUrl);
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
            System.err.println("appendToTextFileSync() - " + command);
            Process p = new ProcessBuilder()
                            .directory(file)
                            .command("echo","hello world")
                            .command("/bin/sh", "-c", command)
                                            //"touch '" + queueFile + "'; echo '" + id + ":" + iUrl + "' >> '" + queueFile + "'"
                                                            .inheritIO().start();
            p.waitFor();
            if (p.exitValue() == 0) {
                    System.err.println("appendToTextFileSync() - successfully appended 4 "
                                    + iUrl);
            } else {
                    System.err.println("appendToTextFileSync() - error appending " + iUrl);
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
					System.err.println("appendToTextFile() - " + command);
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
							System.err.println("appendToTextFile() - successfully appended 5 "
									+ iUrl + " to " + queueFile);
						} else {
							System.err.println("appendToTextFile() - error appending " + iUrl);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			new Thread(r).start();
		}
		
		private static String getTitle(final URL iUrl) {
			System.err.println("YurlStash.YurlResource.getTitle() - are we still using this? If not, delete this.");
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
											System.err.println("YurlResource.getTitle() - " + e.getMessage());
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
			System.err.println("Yurl.YurlResource.changeImage() - success: " + iUrl + " :: " + imageUrl);

			removeCategoryCacheAsync(iCategoryId);

			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString()).type("application/json")
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
			
			System.err.println("Yurl.YurlResource.move() begin");
			
			appendToTextFileSync(iUrl, iNewParentId.toString(), QUEUE_FILE, YurlStash.QUEUE_FILE_TXT_MASTER, created);
			
			System.err.println("Yurl.YurlResource.move() 2");
			appendToTextFileSync(iUrl, "-" + iCurrentParentId.toString(), QUEUE_DIR, YurlStash.QUEUE_FILE_TXT_DELETE, created);
			System.err.println("Yurl.YurlResource.move() 4");
			
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
			
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString())
					.type("application/json").build();
		}
	}

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
		System.err.println("main() - begin");
		String port = "4447";
    
		// Turn off that stupid Jersey logger.
		// This works in Java but not in Groovy.
		//java.util.Logger.getLogger("org.glassfish.jersey").setLevel(java.util.Level.SEVERE);
		try {
			JdkHttpServerFactory.createHttpServer(
					new URI("http://localhost:" + port + "/"), new ResourceConfig(
							YurlStash.YurlResource.class));
		} catch (Exception e) {
			//	e.printStackTrace();
			System.err.println("Not creating server instance");
		}
	}
}
