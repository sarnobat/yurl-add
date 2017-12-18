import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.io.*;
import java.lang.Deprecated;
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

public class VideoDownload {

@Deprecated
	private static final String YOUTUBE_DOWNLOAD = "/home/sarnobat/bin/youtube_download";
@Deprecated
	private static final Integer ROOT_ID = 45;
@Deprecated
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
//	private static final String TARGET_DIR_PATH = "/media/sarnobat/Unsorted/Videos/";
	
	public static void main(String[] args) {
		if (args.length == 1) {
			System.err.println("[WARNING] No url to download.");
			System.exit(-1);
		}
		String iTargetDirPath = args[0];
		String iVideoUrl = args[1];

		File iTargetDirPath2 ;
		try {
			iTargetDirPath2 = Paths.get(iTargetDirPath).toFile();
			System.out.println("[DEBUG] Running in dir " + iTargetDirPath);
		} catch (Exception e) {
			System.out.println("[WARNING] Using " +System.getProperty("user.dir")+ " instead of passed dir due to: " + e.getMessage())
			iTargetDirPath2 = Paths.get(System.getProperty("user.dir")).toFile();
		}

		if (iTargetDirPath2.exists()) {
		} else {
                        iTargetDirPath2 = Paths.get(System.getProperty("user.dir")).toFile();
		}


		try {
			Process p = new ProcessBuilder()
					.directory(iTargetDirPath2)
					.command(
							ImmutableList.of(YOUTUBE_DOWNLOAD,
									iVideoUrl)).inheritIO().start();
			p.waitFor();
			if (p.exitValue() == 0) {
			} else {
				System.err
						.println("YurlWorldResource.downloadVideoInSeparateThread() - error downloading "
								+ iVideoUrl);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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
