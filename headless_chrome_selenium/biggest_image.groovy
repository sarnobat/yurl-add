import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

/**
 * Ideally this should be done in composition with headless.groovy, and not bother with the headless browser itself.
 *
 */
public class BiggestImage {

	private static final String CHROMEDRIVER_PATH = "/sarnobat.garagebandbroken/trash/chromedriver";

	// private static final String CHROMEDRIVER_PATH =
	// "/home/sarnobat/github/yurl/chromedriver";

	/**
	 * Ascending like du
	 */
	private static List<String> getImagesAscendingSize(String url1) throws MalformedURLException,
			IOException {
		String url = url1.startsWith("http") ? url1 : "http://" + url1;
		String base = getBaseUrl(url);
		// Don't use the chrome binaries that you browse the web with.
		System.setProperty("webdriver.chrome.driver", BiggestImage.CHROMEDRIVER_PATH);
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
			List<String> out = getAllTags(base + "/", source);
			Multimap<Integer, String> imageSizes = getImageSizes(out);
			ret = sortByKey(imageSizes, url);
			if (ret.size() < 1) {
				throw new RuntimeException("2 We're going to get a nullpointerexception later: "
						+ url);
			}
		} finally {
			driver.quit();
		}
		if (ret.size() < 1) {
			throw new RuntimeException("1 We're going to get a nullpointerexception later: " + url);
		}
		return ret;
	}

	private static List<String> sortByKey(Multimap<Integer, String> imageSizes, String pageUrl) {
		ImmutableList.Builder<String> finalList = ImmutableList.builder();

		// Phase 1: Sort by size ascending
		ImmutableList<Integer> sortedList = FluentIterable.from(imageSizes.keySet()).toSortedList(
				Ordering.natural());

		// Phase 2: Put non-JPGs first
		for (Integer size : sortedList) {
			for (String url : imageSizes.get(size)) {
				if (!isJpgFile(url)) {
					if (url.length() > 0) {
						finalList.add(url);
						System.out.println(size + "\t" + url);
					}
				}
			}
		}
		// Phase 3: Put JPGs last
		for (Integer size : sortedList) {
			for (String url : imageSizes.get(size)) {
				if (isJpgFile(url)) {
					finalList.add(url);
					System.out.println(size + "\t" + url);
				}
			}
		}
		return finalList.build();
	}

	private static boolean isJpgFile(String url) {
		return url.matches("(?i).*\\.jpg") || url.matches("(?i).*\\.jpg\\?.*");
	}

	private static Multimap<Integer, String> getImageSizes(List<String> out) {
		ImmutableMultimap.Builder<Integer, String> builder = ImmutableMultimap.builder();
		for (String imgSrc : out) {
			int size = getByteSize(imgSrc);
			builder.put(size, imgSrc);
		}
		return builder.build();
	}

	private static int getByteSize(String absUrl) {
		if (Strings.isNullOrEmpty(absUrl)) {
			return 0;
		}
		URL url;
		try {
			url = new URL(absUrl);
			int contentLength = url.openConnection().getContentLength();
			return contentLength;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	private static String getBaseUrl(String url1) throws MalformedURLException {
		URL url = new URL(url1);
		String file = url.getFile();
		String path;
		if (file.length() == 0) {
			path = url1;
		} else {
			path = url.getFile().substring(0, file.lastIndexOf('/'));
		}
		if (path.startsWith("http")) {
			return path;
		} else {
			String string = url.getProtocol() + "://" + url.getHost() + path;
			return string;
		}
	}

	private static List<String> getAllTags(String baseUrl, String source) throws IOException {
		Document doc = Jsoup.parse(IOUtils.toInputStream(source), "UTF-8", baseUrl);
		Elements tags = doc.getElementsByTag("img");
		return FluentIterable.<Element> from(tags).transform(IMG_TO_SOURCE).toList();
	}

	private static final Function<Element, String> IMG_TO_SOURCE = new Function<Element, String>() {
		@Override
		public String apply(Element e) {
			String absUrl = e.absUrl("src");
			return absUrl;
		}
	};

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
		getImagesAscendingSize(args[0]);
	}
}
