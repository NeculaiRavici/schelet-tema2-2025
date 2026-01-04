package main.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public final class Ticket {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int id;
    private final TicketType type;
    private final String title;
    private final List<Comment> comments = new ArrayList<>();

    private BusinessPriority businessPriority;
    private TicketStatus status;

    private String createdAt = "";
    @Getter @Setter
    private String assignedAt = "";
    private String solvedAt = "";
    private String assignedTo = "";
    private String reportedBy = "";

    // --- Added for Test 3 priority escalation ---
    private String severity = "";
    @Getter @Setter
    private String expertiseArea = "";

    public Ticket(final int id, final TicketType type, final String title,
                  final BusinessPriority businessPriority) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.businessPriority = businessPriority;
    }

    public int getId() { return id; }

    public TicketType getType() { return type; }

    public TicketStatus getStatus() { return status; }

    public String getCreatedAt() { return createdAt; }

    public String getReportedBy() { return reportedBy; }

    public String getAssignedTo() { return assignedTo; }

    public BusinessPriority getBusinessPriority() { return businessPriority; }

    public void setBusinessPriority(final BusinessPriority bp) { this.businessPriority = bp; }

    public String getSeverity() { return severity; }

    public void setSeverity(final String severity) { this.severity = severity; }

    public void setReportedBy(final String reportedBy) { this.reportedBy = reportedBy; }

    public void setCreatedAt(final String createdAt) { this.createdAt = createdAt; }

    public void setStatus(final TicketStatus status) { this.status = status; }

    public void setAssignedTo(final String assignedTo) { this.assignedTo = assignedTo; }

    public ObjectNode toOutputJson() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", id);
        n.put("type", type.name());
        n.put("title", title);
        n.put("businessPriority", businessPriority.name());
        n.put("status", status.name());

        n.put("createdAt", createdAt);
        n.put("assignedAt", assignedAt);
        n.put("solvedAt", solvedAt);
        n.put("assignedTo", assignedTo);
        n.put("reportedBy", reportedBy);

        ArrayNode cArr = n.putArray("comments");
        for (Comment c : comments) {
            cArr.add(c.toJson());
        }
        return n;
    }
    public ObjectNode toAssignedTicketJson() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", id);
        n.put("type", type.name());
        n.put("title", title);
        n.put("businessPriority", businessPriority.name());
        n.put("status", status.name());
        n.put("createdAt", createdAt);
        n.put("assignedAt", assignedAt);
        n.put("reportedBy", reportedBy);

        ArrayNode cArr = n.putArray("comments");
        for (Comment c : comments) {
            cArr.add(c.toJson());
        }
        return n;
    }

}
