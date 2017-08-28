import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Neo4j dependencies removed.
 */
public class YurlCounts {
    private static final String QUEUE_FILE_TXT_MASTER = System.getProperty("user.home") + "/sarnobat.git/" + "yurl_master.txt";
	private static final String CACHE_DIR = System.getProperty("user.home") + "/github/yurl/tmp/";
	private static final String CACHE_COUNTS = CACHE_DIR + "/counts/categoryCounts.txt";
	private static final String CATEGORY_NAMES_FILE = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_categories_master.txt";
	
	@Path("yurl")
	// TODO: Rename to YurlResource
	public static class YurlResource { // Must be public


		static {
			// We can't put this in the constructor because multiple instances will get created
		}

		// This only gets invoked when it receives the first request
		// Multiple instances get created
		YurlResource() {
			// We can't put the auto downloader in main()
			// then either it will be called every time the cron job is executed,
			// or not until the server terminates unexceptionally (which never happens).
		}
		
		

		@GET
		@Path("count_non_recursive")
		@Produces("application/json")
		// It's better to do this in a separate Ajax request because it's fast and we can get an idea if database queries are working.
		public Response countNonRecursive(@QueryParam("rootId") String iRootId)
				throws Exception {
			
			System.out.println("YurlCounts.YurlResource.countNonRecursive()");
			checkNotNull(iRootId);
			try {
				
				Map<String, Integer> categoryCounts = getCategoryCounts(CACHE_COUNTS, QUEUE_FILE_TXT_MASTER);
				//System.out.println("YurlCounts.YurlResource.countNonRecursive() 2 : getCategoryCounts = " + categoryCounts.toString());
				JSONObject result = new JSONObject();
				Integer value = categoryCounts.get(iRootId);
				System.out.println("YurlCounts.YurlResource.countNonRecursive() 3 value " + value );
				result.put("count", value);
				
				Map<String, String> categoryNames = getCategoryNames(CATEGORY_NAMES_FILE);
				//System.out.println("YurlCounts.YurlResource.countNonRecursive() categoryNames  = " + categoryNames );
				String value2 = categoryNames.get(iRootId);
				System.out.println("YurlCounts.YurlResource.countNonRecursive() 4 -" + value2);
				result.put("name", value2);
				System.out.println("YurlCounts.YurlResource.countNonRecursive() 5 result = " + result);

				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(result.toString()).type("application/json")
						.build();
			} catch (Exception e) {
				System.out.println("YurlCounts.YurlResource.countNonRecursive() error");
				e.printStackTrace();
				throw e;
			}
		}

		private Map<String, Integer> getCategoryCounts(String cacheCountsFile, String queueFile) throws IOException {
			System.out.println("YurlCounts.YurlResource.getCategoryCounts() begin");
			final File file = Paths.get(cacheCountsFile).toFile();
			if (file.exists()) {
				// read from the cache counts file
				List<String> lines = FileUtils.readLines(file, "UTF-8");
				return buildMapOfCountsFromCache(lines);
			} else {
				// read from the master file
				File masterFile = Paths.get(queueFile).toFile();
				List<String> lines = FileUtils.readLines(masterFile, "UTF-8");
				final Map<String, Integer> categoryCountsMap = buildMapOfCountsFromMaster(lines);
				new Thread() {
					@Override
					public void run() {
						try {
							writeCountsToCacheFile(categoryCountsMap, file);
						} catch (IOException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
				}.start();
				return categoryCountsMap;
			}
		}

		private static void writeCountsToCacheFile(
				Map<String, Integer> categoryCountsMap, File file) throws IOException {
			System.out
					.println("YurlCounts.YurlResource.writeCountsToCacheFile()");
			StringBuffer sb = new StringBuffer();
			for (String categoryId : categoryCountsMap.keySet()) {
				sb.append(categoryId);
				sb.append("::");
				sb.append(categoryCountsMap.get(categoryId));
				sb.append("\n");
			}
			FileUtils.writeStringToFile(file, sb.toString(), "UTF-8");
		}


		private static Map<String, Integer> buildMapOfCountsFromCache(
				List<String> lines) {
			System.out
					.println("YurlCounts.YurlResource.buildMapOfCountsFromCache()");
			Map<String, Integer> counts = new HashMap<String, Integer> ();
			for (String line : lines ) {
				String[] elems = line.split("::");
				String categoryId = elems[0];
				int count = Integer.parseInt(elems[1]);
				counts.put(categoryId, count);
			}
			return counts;
		}

		private Map<String, Integer> buildMapOfCountsFromMaster(List<String> lines) {
			System.out
					.println("YurlCounts.YurlResource.buildMapOfCountsFromMaster() lines = " + lines.size());
			Map<String, Integer> categoryCountsMap = new HashMap<String, Integer>();
			for (String line : lines) {
				String[] elements = line.split("::");
				if (elements.length < 3) {
					System.out
							.println("YurlCounts.YurlResource.buildMapOfCountsFromMaster() bad line: " + line);
					continue;
				}
				String categoryIdElement = elements[0];

				if (categoryCountsMap.containsKey(categoryIdElement)) {
					Integer value = categoryCountsMap.get(categoryIdElement);
					categoryCountsMap.put(categoryIdElement, ++value);
				} else {
					categoryCountsMap.put(categoryIdElement, 1);
				}
			}
			return categoryCountsMap;
		}

		private Map<String, String> getCategoryNames(String categoryNamesFile) throws IOException {
			System.out.println("YurlCounts.YurlResource.getCategoryNames()");
			List<String> lines = FileUtils.readLines(Paths.get(categoryNamesFile).toFile(), "UTF-8");
			Map<String, String> names = new HashMap<String, String> ();
			for (String line : lines) {
				String[] elems = line.split("::");
				//System.out.println("YurlCounts.YurlResource.getCategoryNames() elems " + elems);
				String categoryId = elems[0];
				String name = elems[1];
				names.put(categoryId, name);
			}
			System.out.println("YurlCounts.YurlResource.getCategoryNames() end");
			return names;
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
			port = cmd.getOptionValue("p", "4438");

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
//	e.printStackTrace();
			System.out.println("Not creating server instance");
		}
	}
}
