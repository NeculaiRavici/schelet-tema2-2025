package main;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.core.CommandFacade;
import main.core.SystemState;
import main.model.User;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * App represents the main application logic that processes input commands,
 * generates outputs, and writes them to a file.
 */
public class App {
    private App() {
    }

    private static final String INPUT_USERS_FIELD = "input/database/users.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter WRITER =
            new ObjectMapper().writer().withDefaultPrettyPrinter();

    /**
     * Runs the application: reads commands from an input file,
     * processes them, generates results, and writes them to a file.
     *
     * @param inputPath  path to the input file containing commands
     * @param outputPath path to the file where results should be written
     */
    public static void run(final String inputPath, final String outputPath) {
        List<ObjectNode> outputs = new ArrayList<>();

        // 1) Load users
        try {
            ArrayNode usersArray = (ArrayNode) MAPPER.readTree(new File(INPUT_USERS_FIELD));
            for (JsonNode userNode : usersArray) {
                User u = User.fromJSON(userNode);
                SystemState.getInstance().addUser(u);
            }
        } catch (IOException e) {
            // If users can't be loaded, we can't do much. Still write empty output.
            writeOutput(outputPath, outputs);
            return;
        }

        // 2) Load commands
        ArrayNode commands;
        try {
            commands = (ArrayNode) MAPPER.readTree(new File(inputPath));
        } catch (IOException e) {
            writeOutput(outputPath, outputs);
            return;
        }

        // 3) Process commands via facade
        CommandFacade facade = new CommandFacade();
        Iterator<JsonNode> it = commands.elements();
        while (it.hasNext() && !SystemState.getInstance().isStopped()) {
            JsonNode cmdNode = it.next();
            ObjectNode out = facade.execute(cmdNode);
            if (out != null) {
                outputs.add(out);
            }
        }

        // 4) Write outputs
        writeOutput(outputPath, outputs);
    }

    private static void writeOutput(final String outputPath, final List<ObjectNode> outputs) {
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            WRITER.withDefaultPrettyPrinter().writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("error writing to output file: " + e.getMessage());
        }
    }
}
