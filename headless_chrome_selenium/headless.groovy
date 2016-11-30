import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;
import javax.ws.rs.Path;

import org.apache.commons.io.FilenameUtils;
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

public class Headless {

	private static final String CHROMEDRIVER_PATH = "/sarnobat.garagebandbroken/trash/chromedriver";
	// private static final String CHROMEDRIVER_PATH =
	// "/home/sarnobat/github/yurl/chromedriver";
	public static final String YOUTUBE_DOWNLOAD = "/home/sarnobat/bin/youtube_download";
	public static final Integer ROOT_ID = 45;
	private static final String CYPHER_URI = "http://netgear.rohidekar.com:7474/db/data/cypher";
	private static final String TARGET_DIR_PATH_IMAGES = "/media/sarnobat/Unsorted/images/";
	private static final String TARGET_DIR_PATH_IMAGES_OTHER = "/media/sarnobat/Unsorted/images/other";

	@Path("yurl")
	// --------------------------------------------------------------------------------------
	// Write operations
	// --------------------------------------------------------------------------------------
	private static void launchAsynchronousTasks(String iUrl, String id, Integer iCategoryId) {

		String targetDirPathImages;
		if (iCategoryId.longValue() == 29172) {
			targetDirPathImages = TARGET_DIR_PATH_IMAGES_OTHER;
		} else {
			targetDirPathImages = TARGET_DIR_PATH_IMAGES;
		}
		DownloadImage.downloadImageInSeparateThread(iUrl, targetDirPathImages, CYPHER_URI, id);
	}

	private static class DownloadImage {
		private static void downloadImageInSeparateThread(final String iUrl2,
				final String targetDirPath, final String cypherUri, final String id) {
			final String iUrl = iUrl2.replaceAll("\\?.*", "");
			Runnable r = new Runnable() {
				// @Override
				public void run() {
					System.out.println("downloadImageInSeparateThread() - " + iUrl + " :: "
							+ targetDirPath);
					if (iUrl.toLowerCase().contains(".jpg")) {
					} else if (iUrl.toLowerCase().contains(".jpeg")) {
					} else if (iUrl.toLowerCase().contains(".png")) {
					} else if (iUrl.toLowerCase().contains(".gif")) {
					} else if (iUrl.toLowerCase().contains("gstatic")) {
					} else {
						return;
					}
					try {
						saveImage(iUrl, targetDirPath);
					} catch (Exception e) {
						System.out
								.println("YurlWorldResource.downloadImageInSeparateThread(): 1 Biggest image couldn't be determined"
										+ e.getMessage());
					}
				}
			};
			new Thread(r).start();
		}

		private static void saveImage(String urlString, String targetDirPath)
				throws IllegalAccessError, IOException {
			System.out.println("saveImage() - " + urlString + "\t::\t" + targetDirPath);
			String extension = FilenameUtils.getExtension(urlString);
			ImageIO.write(
					ImageIO.read(new URL(urlString)),
					extension,
					new File(determineDestinationPathAvoidingExisting(
							targetDirPath
									+ "/"
									+ URLDecoder.decode(FilenameUtils.getBaseName(urlString)
											.replaceAll("/", "-"), "UTF-8") + "." + extension)
							.toString()));
			System.out.println("saveImage() - SUCCESS: " + urlString + "\t::\t" + targetDirPath);
		}

		private static java.nio.file.Path determineDestinationPathAvoidingExisting(
				String iDestinationFilePath) throws IllegalAccessError {
			String theDestinationFilePathWithoutExtension = iDestinationFilePath.substring(0,
					iDestinationFilePath.lastIndexOf('.'));
			String extension = FilenameUtils.getExtension(iDestinationFilePath);
			java.nio.file.Path oDestinationFile = Paths.get(iDestinationFilePath);
			while (Files.exists(oDestinationFile)) {
				theDestinationFilePathWithoutExtension += "1";
				iDestinationFilePath = theDestinationFilePathWithoutExtension + "." + extension;
				oDestinationFile = Paths.get(iDestinationFilePath);
			}
			if (Files.exists(oDestinationFile)) {
				throw new IllegalAccessError("an existing file will get overwritten");
			}
			return oDestinationFile;
		}
	}

	// ----------------------------------------------------------------------------
	// Read operations
	// ----------------------------------------------------------------------------

	private static class BiggestImage {

		static List<String> getImagesDescendingSize(String url) throws MalformedURLException,
				IOException {
			String base = getBaseUrl(url);
			// Don't use the chrome binaries that you browse the web with.
			System.setProperty("webdriver.chrome.driver", Headless.CHROMEDRIVER_PATH);

			// HtmlUnitDriver and FirefoxDriver didn't work. Thankfully
			// ChromeDriver does
			WebDriver driver = new ChromeDriver();
			List<String> ret = ImmutableList.of();
			try {
				driver.get(url);
				// TODO: shame there isn't an input stream, then we wouldn't
				// have to
				// store the whole page in memory
				try {
					// We need to let the dynamic content load.
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				String source = driver.getPageSource();
				System.out.println(source);
				List<String> out = getAllTags(base + "/", source);
				Multimap<Integer, String> imageSizes = getImageSizes(out);
				ret = sortByKey(imageSizes);
				if (ret.size() < 1) {
					throw new RuntimeException(
							"2 We're going to get a nullpointerexception later: " + url);
				}
			} finally {
				driver.quit();
			}
			if (ret.size() < 1) {
				throw new RuntimeException("1 We're going to get a nullpointerexception later: "
						+ url);
			}
			return ret;
		}

		private static List<String> sortByKey(Multimap<Integer, String> imageSizes) {
			ImmutableList.Builder<String> finalList = ImmutableList.builder();

			// Phase 1: Sort by size descending
			ImmutableList<Integer> sortedList = FluentIterable.from(imageSizes.keySet())
					.toSortedList(Ordering.natural().reverse());

			// Phase 2: Put JPGs first
			for (Integer size : sortedList) {
				for (String url : imageSizes.get(size)) {
					if (isJpgFile(url)) {
						finalList.add(url);
						System.out.println("BiggestImage.sortByKey() - " + size + "\t" + url);
					}
				}
			}
			for (Integer size : sortedList) {
				for (String url : imageSizes.get(size)) {
					if (!isJpgFile(url)) {
						finalList.add(url);
						System.out.println("BiggestImage.sortByKey() - " + size + "\t" + url);
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
			String string = url.getProtocol() + "://" + url.getHost() + path;
			return string;
		}

		private static List<String> getAllTags(String baseUrl, String source) throws IOException {
			Document doc = Jsoup.parse(IOUtils.toInputStream(source), "UTF-8", baseUrl);
			Elements tags = doc.getElementsByTag("img");
			return FluentIterable.<Element> from(tags).transform(IMG_TO_SOURCE).toList();
		}

		private static final Function<Element, String> IMG_TO_SOURCE = new Function<Element, String>() {
			@Override
			public String apply(Element e) {
				return e.absUrl("src");
			}
		};
	}

	// I hope this is the same as JSONObject.Null (not capitals)
	@Deprecated
	// Does not compile in Eclipse, but does compile in groovy
	public static final Object JSON_OBJECT_NULL = null;// new Null()

	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
		BiggestImage.getImagesDescendingSize("http://www.google.com");
	}
}
