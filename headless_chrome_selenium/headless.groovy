import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;

import org.json.JSONException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import com.google.common.collect.ImmutableList;

public class Headless {

	private static final String CHROMEDRIVER_PATH = "/sarnobat.garagebandbroken/trash/chromedriver";

	// private static final String CHROMEDRIVER_PATH =
	// "/home/sarnobat/github/yurl/chromedriver";

	private static List<String> getGeneratedHtml(String url1) throws MalformedURLException, IOException {
		String url = url1.startsWith("http") ? url1 : "http://" + url1;
		// Don't use the chrome binaries that you browse the web with.
		System.setProperty("webdriver.chrome.driver", Headless.CHROMEDRIVER_PATH);
		System.setProperty("webdriver.chrome.logfile", "/dev/null");
		System.setProperty("webdriver.chrome.args", "disable-logging");
		System.setProperty("webdriver.chrome.silentOutput", "true");

		// HtmlUnitDriver and FirefoxDriver didn't work. Thankfully
		// ChromeDriver does
		WebDriver driver = new ChromeDriver();
		List<String> ret = ImmutableList.of();
		try {
			driver.get(url);
			// TODO: shame there isn't an input stream, then we wouldn't
			// have to store the whole page in memory
			try {
				// We need to let the dynamic content load.
				Thread.sleep(5000L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			String source = driver.getPageSource();
			System.out.println(source);
		} finally {
			driver.quit();
		}
		return ret;
	}

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
		getGeneratedHtml(args[0]);
	}
}
