package main.core;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.model.Ticket;

import java.util.List;

public class OutputBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ObjectNode root;

    private OutputBuilder(final String command, final String username, final String timestamp) {
        this.root = MAPPER.createObjectNode();
        root.put("command", command);
        root.put("username", username);
        root.put("timestamp", timestamp);
    }

    public static OutputBuilder start(final String command, final String username, final String timestamp) {
        return new OutputBuilder(command, username, timestamp);
    }

    public OutputBuilder error(final String message) {
        root.put("error", message);
        return this;
    }

    public OutputBuilder tickets(final List<Ticket> tickets) {
        ArrayNode tArr = root.putArray("tickets");
        for (Ticket t : tickets) {
            tArr.add(t.toOutputJson());
        }
        return this;
    }

    public ObjectNode build() {
        return root;
    }
}
