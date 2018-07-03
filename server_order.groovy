import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;

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
import org.json.JSONException;
import org.json.JSONObject;
import org.jvnet.hk2.annotations.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

// TODO: Use javax.json.* for immutability
public class YurlOrder {
	
	private static final String YURL_ORDINALS = System.getProperty("user.home") + "/sarnobat.git/db/yurl_flatfile_db/yurl_master_ordinals.txt";

	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	
	@Path("yurl")
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

		// --------------------------------------------------------------------------------------
		// Write operations
		// --------------------------------------------------------------------------------------

		// moveup, move up
		@Deprecated // No longer used, remove
		@GET
		@Path("surpassOrdinalNeo4j")
		@Produces("application/json")
		public Response surpassOrdinalNeo4j(
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
		@Path("surpassOrdinal")
		@Produces("application/json")
		public Response surpassOrdinal(
				@QueryParam("url") String iUrl,
				@QueryParam("categoryId") final String categoryId,
				@Optional @QueryParam("created") String iCreated)
				throws IOException, JSONException {
			
			System.out.println("YurlOrder.YurlResource.surpassOrdinal()");
			
			FileUtils
					.write(Paths
							.get(YURL_ORDINALS).toFile(),
							categoryId + "::" + iUrl + "::" + System.currentTimeMillis() +"\n", "UTF-8", true);

			new Thread() {
				@Override
				public void run() {
					removeCategoryCache(Integer.parseInt(categoryId));
				}
			}.start();
			
			return Response
					.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString()).type("application/json")
					.build();
		}

		private static void removeCategoryCache(Integer iCategoryId) {
			java.nio.file.Path path1 = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/urls/" + iCategoryId + ".json");
			path1.toFile().delete();
        	System.out.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file: " + path1);
        	
        	java.nio.file.Path path = Paths.get(System.getProperty("user.home") + "/github/yurl/tmp/categories/topology/" + iCategoryId + ".txt");
			path.toFile().delete();
        	System.out.println("Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file: " + path);
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

		static JSONObject execute(String iCypherQuery, Map<String, Object> iParams, String... iCommentPrefix) {
			return execute(iCypherQuery, iParams, true, iCommentPrefix);
		}

		// TODO: make this map immutable
		static JSONObject execute(String iCypherQuery,
				Map<String, Object> iParams, boolean doLogging, String... iCommentPrefix) {
			return new JSONObject();
		}
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {

		String port = 4439;
		_parseOptions: {
/*
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
			port = cmd.getOptionValue("p", "4439");

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
*/
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
