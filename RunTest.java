public class RunTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: RunTest <input_path> <output_path>");
            return;
        }
        main.App.run(args[0], args[1]);
        System.out.println("Done.");
    }
}
