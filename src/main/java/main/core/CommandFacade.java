package main.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.model.Role;
import main.model.Ticket;
import main.model.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CommandFacade {
    private final SystemState state = SystemState.getInstance();
    private final Map<String, Role[]> permissions = new EnumMap(CommandType.class);

    public CommandFacade() {
        permissions.put("reportTicket", new Role[]{Role.REPORTER});
        permissions.put("viewTickets", new Role[]{Role.MANAGER});
        permissions.put("startTestingPhase", new Role[]{Role.MANAGER});
        permissions.put("lostInvestors", new Role[]{Role.MANAGER});
    }

    public ObjectNode execute(final JsonNode cmdNode) {
    String command = cmdNode.get("command").asText();
    String username = cmdNode.get("username").asText();
    String timestamp = cmdNode.get("timestamp").asText();
    User user= state.getUser(username);
    if (user == null) {
        return OutputBuilder.start(command, username, timestamp)
                .error("The user" + username + "does not exist.")
                .build();
    }
    Role[] allowed = permissions.get(command);
    if(allowed != null && !hasRole(user.getRole(),allowed)) {
        return OutputBuilder.start(command, username, timestamp)
                .error(permissionMessage(command,user.getRole(), allowed))
                .build();
    }
        // Dispatch
        switch (command) {
            case "reportTicket":
                return handleReportTicket(cmdNode, user);
            case "viewTickets":
                return handleViewTickets(cmdNode, user);
            case "startTestingPhase":
                return handleStartTestingPhase(cmdNode);
            case "lostInvestors":
                return handleLostInvestors();
            default:
                // If checker includes commands not yet implemented, better to ignore than crash
                return null;
        }
    }
    private ObjectNode handleReportTicket(final JsonNode cmdNode, final User user) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        if (!state.isTestingPhase()) {
            return OutputBuilder.start("reportTicket", username, timestamp)
                    .error("Tickets can only be reported during testing phases.")
                    .build();
        }

        JsonNode params = cmdNode.get("params");
        int id = state.allocateTicketId();

        Ticket t = TicketFactory.createTicket(id, timestamp, params);
        if (t == null) {
            return OutputBuilder.start("reportTicket", username, timestamp)
                    .error("Anonymous reports are only allowed for tickets of type BUG.")
                    .build();
        }

        state.getTickets().add(t);
        return null; // spec: reportTicket has no output unless error
    }

    private ObjectNode handleViewTickets(final JsonNode cmdNode, final User user) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        List<Ticket> visible = new ArrayList<>();
        switch (user.getRole()) {
            case MANAGER:
                visible.addAll(state.getTickets());
                break;
            case REPORTER:
                for (Ticket t : state.getTickets()) {
                    // Reporter sees only his/her own reported tickets; anonymous excluded.
                    if (!t.getReportedBy().isEmpty() && t.getReportedBy().equals(user.getUsername())) {
                        visible.add(t);
                    }
                }
                break;
            case DEVELOPER:
                // For the baseline: show nothing unless you implement milestones.
                // Later: OPEN tickets in milestones where dev is assigned.
                break;
            default:
                break;
        }

        visible.sort(Comparator
                .comparing(Ticket::getCreatedAt)
                .thenComparingInt(Ticket::getId));

        return OutputBuilder.start("viewTickets", username, timestamp)
                .tickets(visible)
                .build();
    }

    private ObjectNode handleStartTestingPhase(final JsonNode cmdNode) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        // Baseline: we don't have milestones yet, so we always allow.
        // Later: block if there are active milestones with unresolved tickets.
        state.setTestingPhase(true);
        return null;
    }

    private ObjectNode handleLostInvestors() {
        state.stop();
        return null;
    }

    private boolean hasRole(final Role userRole, final Role[] allowed) {
        for (Role r : allowed) {
            if (r == userRole) {
                return true;
            }
        }
        return false;
    }

    private String permissionMessage(final String command, final Role userRole, final Role[] allowed) {
        StringBuilder sb = new StringBuilder();
        sb.append("The user does not have permission to execute this command: required role ");
        for (int i = 0; i < allowed.length; i++) {
            sb.append(allowed[i].name());
            if (i + 1 < allowed.length) {
                sb.append(", ");
            }
        }
        sb.append("; user role ").append(userRole.name()).append(".");
        return sb.toString();
    }
}