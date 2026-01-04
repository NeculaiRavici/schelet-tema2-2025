package main.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Ticket {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Getter
    private final int id;
    private final TicketType type;
    private final String title;
    private BusinessPriority businessPriority;
    @Setter
    private TicketStatus status;
    @Getter @Setter
    private String createdAt;
    private String assignedAt="";
    private String solvedAt="";
    private String assignedTo="";
    @Getter @Setter
    private String reportedBy="";
    private final List<Comment> comments=new ArrayList<>();
    public Ticket(final int id,final TicketType type,final String title,
                  final BusinessPriority businessPriority) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.businessPriority = businessPriority;
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
}
