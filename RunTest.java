import main.App;
import java.io.File;
import java.nio.file.Files;

public class RunTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: RunTest <input> <output>");
            return;
        }
        App.run(args[0], args[1]);
        System.out.println("Done. Output written to: " + args[1]);
    }
}
