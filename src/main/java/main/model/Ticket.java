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
    private final List<TicketAction> actions = new ArrayList<>();
    private BusinessPriority businessPriority;
    private TicketStatus status;

    private String createdAt = "";
    @Getter
    @Setter
    private String assignedAt = "";
    @Getter
    private String solvedAt = "";
    private String assignedTo = "";
    private String reportedBy = "";

    // --- Added for Test 3 priority escalation ---
    private String severity = "";
    @Getter
    @Setter
    private String expertiseArea = "";

    public Ticket(final int id, final TicketType type, final String title,
                  final BusinessPriority businessPriority) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.businessPriority = businessPriority;
    }

    public int getId() {
        return id;
    }

    public TicketType getType() {
        return type;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public BusinessPriority getBusinessPriority() {
        return businessPriority;
    }

    public void setBusinessPriority(final BusinessPriority bp) {
        this.businessPriority = bp;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(final String severity) {
        this.severity = severity;
    }

    public void setReportedBy(final String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public void setCreatedAt(final String createdAt) {
        this.createdAt = createdAt;
    }

    public void setStatus(final TicketStatus status) {
        this.status = status;
    }

    public void setAssignedTo(final String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void addComment(final Comment c) {
        comments.add(c);
    }


    public void addAction(final TicketAction a) {
        actions.add(a);
    }

    public List<TicketAction> getActions() {
        return actions;
    }
    // stack of previous statuses for undo
    private final java.util.Deque<TicketStatus> statusHistory = new java.util.ArrayDeque<>();

    public void pushStatusHistory(final TicketStatus prev) {
        statusHistory.push(prev);
    }

    public TicketStatus popStatusHistory() {
        return statusHistory.isEmpty() ? null : statusHistory.pop();
    }

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

    public boolean undoLastCommentBy(final String author) {
        for (int i = comments.size() - 1; i >= 0; i--) {
            if (author.equals(comments.get(i).getAuthor())) {
                comments.remove(i);
                return true;
            }
        }
        return false;
    }
    public ObjectNode toHistoryJson() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", id);
        n.put("title", title);
        n.put("status", status.name());

        ArrayNode aArr = n.putArray("actions");
        for (TicketAction a : actions) {
            aArr.add(a.toJson());
        }

        ArrayNode cArr = n.putArray("comments");
        for (Comment c : comments) {
            cArr.add(c.toJson());
        }

        return n;
    }
    private java.util.List<String> tempMatchingWords = java.util.Collections.emptyList();

    public String getTitle() {
        return title;
    }

    public void setTempMatchingWords(final java.util.List<String> words) {
        this.tempMatchingWords = (words == null) ? java.util.Collections.emptyList() : new java.util.ArrayList<>(words);
    }

    public java.util.List<String> getTempMatchingWords() {
        return tempMatchingWords;
    }

    public void clearTempMatchingWords() {
        this.tempMatchingWords = java.util.Collections.emptyList();
    }
    private String frequency = "";        // BUG
    private String businessValue = "";    // FEATURE_REQUEST / UI_FEEDBACK
    private String customerDemand = "";   // FEATURE_REQUEST
    private Integer usabilityScore = null; // UI_FEEDBACK

    public String getFrequency() { return frequency; }
    public void setFrequency(final String frequency) { this.frequency = frequency; }

    public String getBusinessValue() { return businessValue; }
    public void setBusinessValue(final String businessValue) { this.businessValue = businessValue; }

    public String getCustomerDemand() { return customerDemand; }
    public void setCustomerDemand(final String customerDemand) { this.customerDemand = customerDemand; }

    public Integer getUsabilityScore() { return usabilityScore; }
    public void setUsabilityScore(final Integer usabilityScore) { this.usabilityScore = usabilityScore; }

}
