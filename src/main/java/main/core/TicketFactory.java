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
        String reportedBy = params.get("reportedBy").asText();

        // Anonymous rule (as in Test 1)
        if (reportedBy.isEmpty()) {
            if (type != TicketType.BUG) {
                return null;
            }
            priority = BusinessPriority.LOW;
        }

        Ticket t = new Ticket(id, type, title, priority);
        t.setStatus(TicketStatus.OPEN);
        t.setCreatedAt(createdAt);
        t.setReportedBy(reportedBy);

        // --- Added: store BUG severity for Test 3 escalation ---
        JsonNode sev = params.get("severity");
        if (sev != null && sev.isTextual()) {
            t.setSeverity(sev.asText());
        }
        com.fasterxml.jackson.databind.JsonNode exp = params.get("expertiseArea");
        if (exp != null && exp.isTextual()) {
            t.setExpertiseArea(exp.asText());
        }
        JsonNode freq = params.get("frequency");
        if (freq != null && freq.isTextual()) t.setFrequency(freq.asText());

        JsonNode bv = params.get("businessValue");
        if (bv != null && bv.isTextual()) t.setBusinessValue(bv.asText());

        JsonNode cd = params.get("customerDemand");
        if (cd != null && cd.isTextual()) t.setCustomerDemand(cd.asText());

        JsonNode us = params.get("usabilityScore");
        if (us != null && us.isInt()) t.setUsabilityScore(us.asInt());

        return t;
    }
}
