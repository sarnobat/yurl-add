import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import com.github.axet.vget.VGet;
import com.github.axet.vget.info.VideoInfo.VideoQuality;



public class Test {
	public static void main(String[] args) throws MalformedURLException {
		VGet v = new VGet(new URL("https://www.youtube.com/watch?v=DQ3Pj-6R8oc"), new File("/media/sarnobat/Unsorted/Videos"));
		System.out
				.println("downloadVideoInSeparateThread() - About to start downloading");
		v.getVideo().setVideoQuality(VideoQuality.p1080);

		System.out.println(v.getVideo().getWeb().toString());
		System.out.println(v.getVideo().getVideoQuality());
		v.download();
		System.out
				.println("downloadVideoInSeparateThread() - Download successful. Updating database");

	}
}

