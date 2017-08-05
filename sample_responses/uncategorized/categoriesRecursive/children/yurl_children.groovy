import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class YurlChildren {
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject uncategorized = new JSONObject();
		JSONArray categoriesRecursive = new JSONArray(
				FileUtils.readFileToString(
						Paths.get(
								System.getProperty("user.home")
										+ "/Desktop/github-repositories/yurl/sample_responses/uncategorized/categoriesRecursive/children/children.json")
								.toFile(), "UTF-8"));
		uncategorized.put("children", categoriesRecursive);

		System.out.println(uncategorized.toString(2));
	}
}
