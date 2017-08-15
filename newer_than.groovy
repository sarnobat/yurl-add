import java.util.Date;
import java.util.Scanner;

public class NewerThan {
	public static void main(String[] args) {
		long threshold = Long.parseLong(args[0]);
		if (threshold < 9999999999L) {
			threshold = threshold * 1000;
		}
		String line;
		Scanner stdin = new Scanner(System.in);
		while (stdin.hasNextLine() && !(line = stdin.nextLine()).equals("")) {
			String[] tokens = line.split("::");
			if (tokens.length != 3) {
				System.err.println("bad line: " + line);
				continue;
			}
			//System.err.println("NewerThan.main()" + line);
			try {
			Long l = Long.parseLong(tokens[2]);
			if (l < 9999999999L) {
				l = l * 1000;
			}
			if (l > threshold) {
				System.out.println(line);
				System.err.println("NewerThan.main() new: " + new Date(l));
			} else {
				System.out.println("NewerThan.main() --------------------------");
				System.out.println("NewerThan.main() l         = " + l);
				System.out.println("NewerThan.main() threshold = " + threshold);
				System.err.println("NewerThan.main() too old: " + new Date(l));
			}
			} catch (Exception e) {
				System.err.println("NewerThan.main() " + e + ": " + line);
				
			}
		}
		stdin.close();
	}
}

