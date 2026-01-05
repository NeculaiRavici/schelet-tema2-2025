package main.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.model.Milestone;
import main.model.Ticket;

import java.util.List;

public final class OutputBuilder {
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
        ArrayNode arr = root.putArray("tickets");
        for (Ticket t : tickets) {
            arr.add(t.toOutputJson());
        }
        return this;
    }

    public OutputBuilder milestones(final List<ObjectNode> milestoneNodes) {
        ArrayNode arr = root.putArray("milestones");
        for (ObjectNode n : milestoneNodes) {
            arr.add(n);
        }
        return this;
    }

    public ObjectNode build() {
        return root;
    }
    public OutputBuilder assignedTickets(final List<Ticket> tickets) {
        ArrayNode arr = root.putArray("assignedTickets");
        for (Ticket t : tickets) {
            arr.add(t.toAssignedTicketJson());
        }
        return this;
    }
    public OutputBuilder putString(final String key, final String value) {
        root.put(key, value);
        return this;
    }

    public OutputBuilder resultsEmpty() {
        root.putArray("results");
        return this;
    }

}
