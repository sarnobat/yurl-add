import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONObject;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("yurl")
	public static class HelloWorldResource { // Must be public

		final String CYPHER_URI = "http://localhost:7474/db/data/cypher";

		@GET
		@Path("json")
		@Produces("application/json")
		public String json() throws JSONException, org.codehaus.jettison.json.JSONException,
				IOException {

			WebResource resource = Client.create().resource(CYPHER_URI);

			// POST {} to the node entry point URI
			ClientResponse response = resource
					.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON)
					.entity("{ }")
					.post(ClientResponse.class,
							"{\"query\" : \"start n=node(*) where has(n.name) and has (n.key) return n.name,n.key\", \"params\" : {}}");
			InputStream stream = response.getEntityInputStream();
			String s = IOUtils.toString(stream);
			System.out.println(s);
			stream.close();
			response.close();
			JSONObject json = new JSONObject(s);

			return json.get("data").toString();
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4447/"), new ResourceConfig(HelloWorldResource.class));
	}
}
