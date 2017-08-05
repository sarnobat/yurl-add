import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class YurlUrls {
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject uncategorized = new JSONObject();
		JSONArray categoriesRecursive = new JSONArray(
				FileUtils.readFileToString(
						Paths.get(
								System.getProperty("user.home")
										+ "/Desktop/github-repositories/yurl/sample_responses/uncategorized/urls/105984.json")
								.toFile(), "UTF-8"));
		uncategorized.put("105984", categoriesRecursive);

		JSONArray urls = new JSONArray(
				FileUtils.readFileToString(
						Paths.get(
								System.getProperty("user.home")
										+ "/Desktop/github-repositories/yurl/sample_responses/uncategorized/urls/106044.json")
								.toFile(), "UTF-8"));
		uncategorized.put("106044", urls);
		System.out.println(uncategorized.toString(2));
	}
}
