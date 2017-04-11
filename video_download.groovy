import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

public class VideoDownloadNeo4j {

	private static final String YOUTUBE_DOWNLOAD = "/home/sarnobat/bin/youtube_download";
	private static final Integer ROOT_ID = 45;
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
//	private static final String TARGET_DIR_PATH = "/media/sarnobat/Unsorted/Videos/";
	
	public static void main(String[] args) {
		String iTargetDirPath = args[0];
		String iVideoUrl = args[1];

		try {
			Process p = new ProcessBuilder()
					.directory(Paths.get(iTargetDirPath).toFile())
					.command(
							ImmutableList.of(YOUTUBE_DOWNLOAD,
									iVideoUrl)).inheritIO().start();
			p.waitFor();
			if (p.exitValue() == 0) {
				writeSuccessToDb(iVideoUrl);
			} else {
				System.err
						.println("YurlWorldResource.downloadVideoInSeparateThread() - error downloading "
								+ iVideoUrl);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void writeSuccessToDb(final String iVideoUrl)
			throws IOException {
                        System.out.println("writeSuccessToDb() - Attempting to record successful download in database...");
		execute("start n=node(*) WHERE n.url = {url} SET n.downloaded_video = {date}",
				ImmutableMap.<String, Object> of("url", iVideoUrl,
						"date", new Long(System.currentTimeMillis())), true, "writeSuccessToDb()");
		System.out.println("writeSuccessToDb() - Download recorded in database");
	}

	private static JSONObject execute(String iCypherQuery,
			Map<String, Object> iParams, boolean doLogging, String... iCommentPrefix) {
		String commentPrefix = iCommentPrefix.length > 0 ? iCommentPrefix[0] + " " : "";
		if (doLogging) {
			System.out.println(commentPrefix + "-\t" + iCypherQuery);
			System.out.println(commentPrefix + "-\tparams - " + iParams);
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
			System.out.println(commentPrefix + "-\tFAILED:\n\t" + iCypherQuery + "\n\tparams: "
					+ iParams);
			try {
				throw new RuntimeException(IOUtils.toString(theResponse.getEntityInputStream()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
		String theNeo4jResponse ;
		try {
			// Do not inline this. We need to close the stream after
			// copying
			theNeo4jResponse = IOUtils.toString(theResponse.getEntityInputStream());
			theResponse.getEntityInputStream().close();
			theResponse.close();
			if (doLogging) {
				System.out.println(commentPrefix + "-\tSUCCESS - end");
			}
			return new JSONObject(theNeo4jResponse);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		}
	}
}
