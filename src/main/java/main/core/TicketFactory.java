package main.core;

import com.fasterxml.jackson.databind.JsonNode;
import main.model.BusinessPriority;
import main.model.Ticket;
import main.model.TicketStatus;
import main.model.TicketType;

public final class TicketFactory {
    private TicketFactory() {
    }

    public static Ticket createTicket(final int id, final String createdAt, final JsonNode params) {
        TicketType type = TicketType.valueOf(params.get("type").asText());
        String title = params.get("title").asText();
        BusinessPriority priority = BusinessPriority.valueOf(params.get("businessPriority").asText());
        String reportedBy = params.get("reportedBy").asText(); // can be ""
        // Note: username != reportedBy is allowed; reportedBy is kept as given.

        // Anonymous rule (from spec + ref test):
        if (reportedBy.isEmpty()) {
            if (type != TicketType.BUG) {
                // caller handles error; but keep consistent:
                return null;
            }
            priority = BusinessPriority.LOW;
        }

        Ticket t = new Ticket(id, type, title, priority);
        t.setStatus(TicketStatus.OPEN);
        t.setCreatedAt(createdAt);
        t.setReportedBy(reportedBy);
        return t;
    }
}
