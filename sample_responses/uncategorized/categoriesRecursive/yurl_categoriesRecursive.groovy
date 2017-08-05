import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class YurlCategoriesRecursive {
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject uncategorized = new JSONObject();
		JSONObject categoriesRecursive = new JSONObject(
				FileUtils.readFileToString(
						Paths.get(
								System.getProperty("user.home")
										+ "/Desktop/github-repositories/yurl/sample_responses/uncategorized/categoriesRecursive.json")
								.toFile(), "UTF-8"));
		uncategorized.put("categoriesRecursive", categoriesRecursive);

		System.out.println(uncategorized.toString(2));
	}
}
