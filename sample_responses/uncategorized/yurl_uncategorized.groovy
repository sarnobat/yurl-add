import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class YurlUncategorized {
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject uncategorized = new JSONObject();
		JSONObject categoriesRecursive = new JSONObject(
				FileUtils
						.readFileToString(
								Paths.get(
										"/sarnobat.garagebandbroken/Desktop/github-repositories/yurl/sample_responses/uncategorized/categoriesRecursive.json")
										.toFile(), "UTF-8"));
		uncategorized.put("categoriesRecursive", categoriesRecursive);

		JSONObject urls = new JSONObject(
				FileUtils
						.readFileToString(
								Paths.get(
										"/sarnobat.garagebandbroken/Desktop/github-repositories/yurl/sample_responses/uncategorized/urls.json")
										.toFile(), "UTF-8"));
		uncategorized.put("urls", urls);
		System.out.println(uncategorized.toString(2));
	}
}
