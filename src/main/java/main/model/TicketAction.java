package main.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;

public final class TicketAction {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Getter
    private final String action;     // "ADDED_TO_MILESTONE", "ASSIGNED", "STATUS_CHANGED"
    @Getter
    private final String by;
    @Getter
    private final String timestamp;

    private final String milestone;  // only for ADDED_TO_MILESTONE
    private final String from;
    @Getter
    private final String to;         // only for STATUS_CHANGED

    private TicketAction(final String action, final String by, final String timestamp,
                         final String milestone, final String from, final String to) {
        this.action = action;
        this.by = by;
        this.timestamp = timestamp;
        this.milestone = milestone;
        this.from = from;
        this.to = to;
    }

    public static TicketAction addedToMilestone(final String milestone, final String by,
                                                final String timestamp) {
        return new TicketAction("ADDED_TO_MILESTONE", by, timestamp, milestone, null, null);
    }

    public static TicketAction assigned(final String by, final String timestamp) {
        return new TicketAction("ASSIGNED", by, timestamp, null, null, null);
    }

    public static TicketAction statusChanged(final String from, final String to,
                                             final String by, final String timestamp) {
        return new TicketAction("STATUS_CHANGED", by, timestamp, null, from, to);
    }

    public ObjectNode toJson() {
        ObjectNode n = MAPPER.createObjectNode();
        if (milestone != null) {
            n.put("milestone", milestone);
        }
        if (from != null) {
            n.put("from", from);
        }
        if (to != null) {
            n.put("to", to);
        }
        n.put("by", by);
        n.put("timestamp", timestamp);
        n.put("action", action);
        return n;
    }
    public static TicketAction deAssigned(final String by, final String timestamp) {
        return new TicketAction("DE-ASSIGNED", by, timestamp, null, null, null);
    }

}
