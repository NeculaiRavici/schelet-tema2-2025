package main.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.model.*;

import java.util.*;

public final class CommandFacade {
    private final SystemState state = SystemState.getInstance();

    private final Map<String, Role[]> permissions = new HashMap<>();

    public CommandFacade() {
        permissions.put("reportTicket", new Role[]{Role.REPORTER});
        permissions.put("viewTickets", new Role[]{Role.REPORTER, Role.DEVELOPER, Role.MANAGER});
        permissions.put("startTestingPhase", new Role[]{Role.MANAGER});
        permissions.put("lostInvestors", new Role[]{Role.MANAGER});

        // Test 2
        permissions.put("createMilestone", new Role[]{Role.MANAGER});
        permissions.put("viewMilestones", new Role[]{Role.MANAGER, Role.DEVELOPER});
        permissions.put("assignTicket", new Role[]{Role.DEVELOPER});
        permissions.put("undoAssignTicket", new Role[]{Role.DEVELOPER});
        permissions.put("viewAssignedTickets", new Role[]{Role.DEVELOPER});

    }

    public ObjectNode execute(final JsonNode cmdNode) {
        String command = cmdNode.get("command").asText();
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        User user = state.getUser(username);
        if (user == null) {
            return OutputBuilder.start(command, username, timestamp)
                    .error("The user " + username + " does not exist.")
                    .build();
        }

        Role[] allowed = permissions.get(command);
        if (allowed != null && !hasRole(user.getRole(), allowed)) {
            return OutputBuilder.start(command, username, timestamp)
                    .error(permissionMessage(command, user.getRole(), allowed))
                    .build();
        }

        switch (command) {
            case "reportTicket":
                return handleReportTicket(cmdNode, user);
            case "viewTickets":
                return handleViewTickets(cmdNode, user);
            case "startTestingPhase":
                return handleStartTestingPhase(cmdNode);
            case "lostInvestors":
                return handleLostInvestors();

            // Test 2
            case "createMilestone":
                return handleCreateMilestone(cmdNode, user);
            case "viewMilestones":
                return handleViewMilestones(cmdNode, user);case "assignTicket":
                return handleAssignTicket(cmdNode, user);
            case "viewAssignedTickets":
                return handleViewAssignedTickets(cmdNode, user);
            case "undoAssignTicket":
                return handleUndoAssignTicket(cmdNode, user);
            default:
                return null;
        }
    }

    // --- Existing handlers (keep yours if already correct) ---

    private ObjectNode handleReportTicket(final JsonNode cmdNode, final User user) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        if (!state.isTestingPhase(timestamp)) {
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
        return null;
    }
    private static int daysUntilDueInclusive(final String nowIso, final String dueIso) {
        java.time.LocalDate now = java.time.LocalDate.parse(nowIso);
        java.time.LocalDate due = java.time.LocalDate.parse(dueIso);
        if (due.isBefore(now)) {
            return 0;
        }
        long diff = java.time.temporal.ChronoUnit.DAYS.between(now, due);
        return (int) diff + 1;
    }

    private void applyPriorityEscalationForView(final String timestamp) {
        for (Ticket t : state.getTickets()) {
            String msName = state.getMilestoneNameForTicket(t.getId());
            if (msName == null) {
                continue;
            }
            Milestone ms = state.getMilestone(msName);
            if (ms == null) {
                continue;
            }
            // only active milestones matter
            if (!ms.isActive(state)) {
                continue;
            }

            int d = daysUntilDueInclusive(timestamp, ms.getDueDate());

            // Test 3 behavior:
            // - within 3 days: raise to HIGH, but if BUG with SEVERE -> CRITICAL immediately
            // - within 2 days: CRITICAL for everyone
            if (d <= 2) {
                t.setBusinessPriority(BusinessPriority.CRITICAL);
                continue;
            }

            if (d <= 3) {
                if (t.getType() == TicketType.BUG && "SEVERE".equals(t.getSeverity())) {
                    t.setBusinessPriority(BusinessPriority.CRITICAL);
                } else {
                    // at least HIGH
                    if (t.getBusinessPriority().ordinal() < BusinessPriority.HIGH.ordinal()) {
                        t.setBusinessPriority(BusinessPriority.HIGH);
                    }
                }
            }
        }
    }

    private ObjectNode handleViewTickets(final JsonNode cmdNode, final User user) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        applyPriorityEscalationForView(timestamp);

        List<Ticket> visible = new ArrayList<>();
        switch (user.getRole()) {
            case MANAGER:
                visible.addAll(state.getTickets());
                break;
            case REPORTER:
                for (Ticket t : state.getTickets()) {
                    if (!t.getReportedBy().isEmpty() && t.getReportedBy().equals(user.getUsername())) {
                        visible.add(t);
                    }
                }
                break;
            case DEVELOPER:
                for (Ticket t : state.getTickets()) {
                    if (t.getStatus() != TicketStatus.OPEN) {
                        continue;
                    }
                    String msName = state.getMilestoneNameForTicket(t.getId());
                    if (msName == null) {
                        continue;
                    }
                    Milestone ms = state.getMilestone(msName);
                    if (ms == null) {
                        continue;
                    }
                    if (ms.isDevAssigned(username)) {
                        visible.add(t);
                    }
                }
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
        String timestamp = cmdNode.get("timestamp").asText();
        state.startTestingPhaseFrom(timestamp);
        return null;
    }

    private ObjectNode handleLostInvestors() {
        state.stop();
        return null;
    }
    private ObjectNode handleCreateMilestone(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();

        // Milestones only during development
        if (state.isTestingPhase(timestamp)) {
            return OutputBuilder.start("createMilestone", username, timestamp)
                    .error("Milestones can only be created during development phases.")
                    .build();
        }

        final String name = cmdNode.get("name").asText();
        final String dueDate = cmdNode.get("dueDate").asText();

        final List<String> blockingFor = new ArrayList<>();
        for (JsonNode n : cmdNode.get("blockingFor")) {
            blockingFor.add(n.asText());
        }

        final List<Integer> tickets = new ArrayList<>();
        for (JsonNode n : cmdNode.get("tickets")) {
            tickets.add(n.asInt());
        }

        final List<String> assignedDevs = new ArrayList<>();
        for (JsonNode n : cmdNode.get("assignedDevs")) {
            assignedDevs.add(n.asText());
        }

        // Validate assigned devs EXIST and are DEVELOPERS (NO subordinate constraint in Test 3)
        for (String dev : assignedDevs) {
            User u = state.getUser(dev);
            if (u == null) {
                return OutputBuilder.start("createMilestone", username, timestamp)
                        .error("The user " + dev + " does not exist.")
                        .build();
            }
            if (u.getRole() != Role.DEVELOPER) {
                return OutputBuilder.start("createMilestone", username, timestamp)
                        .error("The user " + dev + " is not a developer.")
                        .build();
            }
        }

        // Ticket existence check (safe for later tests)
        for (int tid : tickets) {
            if (state.findTicket(tid) == null) {
                return OutputBuilder.start("createMilestone", username, timestamp)
                        .error("The ticket " + tid + " does not exist.")
                        .build();
            }
        }

        // Ticket uniqueness (FIRST conflicting ticket) with EXACT message format for ref_03
        for (int tid : tickets) {
            String existing = state.getMilestoneNameForTicket(tid);
            if (existing != null) {
                return OutputBuilder.start("createMilestone", username, timestamp)
                        .error("Tickets " + tid + " already assigned to milestone " + existing + ".")
                        .build();
            }
        }

        Milestone m = new Milestone(
                name,
                blockingFor,
                dueDate,
                timestamp,
                tickets,
                assignedDevs,
                username
        );

        state.addMilestone(m);
        for (int tid : tickets) {
            state.linkTicketToMilestone(tid, name);
        }

        return null;
    }


    private ObjectNode handleViewMilestones(final JsonNode cmdNode, final User user) {
        String username = cmdNode.get("username").asText();
        String timestamp = cmdNode.get("timestamp").asText();

        List<Milestone> visible = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            for (Milestone m : state.getAllMilestones()) {
                if (m.getCreatedBy().equals(username)) {
                    visible.add(m);
                }
            }
        } else if (user.getRole() == Role.DEVELOPER) {
            for (Milestone m : state.getAllMilestones()) {
                if (m.isDevAssigned(username)) {
                    visible.add(m);
                }
            }
        }

        // Sort: dueDate asc, then name asc (lexicographic)
        visible.sort((a, b) -> {
            int cmp = a.getDueDate().compareTo(b.getDueDate());
            if (cmp != 0) {
                return cmp;
            }
            return a.getName().compareTo(b.getName());
        });

        List<ObjectNode> milestoneNodes = new ArrayList<>();
        for (Milestone m : visible) {
            milestoneNodes.add(m.toOutputJson(state, timestamp));
        }

        return OutputBuilder.start("viewMilestones", username, timestamp)
                .milestones(milestoneNodes)
                .build();
    }

    // --- Helpers ---

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
    private ObjectNode handleAssignTicket(final com.fasterxml.jackson.databind.JsonNode cmdNode, final main.model.User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();
        final int ticketId = cmdNode.get("ticketID").asInt();

        final main.model.Ticket t = state.findTicket(ticketId);
        if (t == null) {
            return OutputBuilder.start("assignTicket", username, timestamp)
                    .error("The ticket " + ticketId + " does not exist.")
                    .build();
        }

        // Only OPEN tickets can be assigned.
        if (t.getStatus() != main.model.TicketStatus.OPEN) {
            return OutputBuilder.start("assignTicket", username, timestamp)
                    .error("Only OPEN tickets can be assigned.")
                    .build();
        }

        // Ticket must belong to a milestone for assignment rules in this task
        final String msName = state.getMilestoneNameForTicket(ticketId);
        final main.model.Milestone ms = (msName == null) ? null : state.getMilestone(msName);

        // Developer object
        final main.model.Developer dev = (user instanceof main.model.Developer) ? (main.model.Developer) user : null;

        // Must be assigned to milestone
        if (ms != null && !ms.isDevAssigned(username)) {
            return OutputBuilder.start("assignTicket", username, timestamp)
                    .error("Developer " + username + " is not assigned to milestone " + ms.getName() + ".")
                    .build();
        }

        // Cannot assign ticket from blocked milestone <name>.
        if (ms != null && ms.isBlocked(state)) {
            return OutputBuilder.start("assignTicket", username, timestamp)
                    .error("Cannot assign ticket " + ticketId + " from blocked milestone " + ms.getName() + ".")
                    .build();
        }

        // Expertise area check (Task 5 specific wording)
        if (dev != null && ms != null) {
            String ticketExp = t.getExpertiseArea(); // e.g. "DB"
            main.model.ExpertiseArea cur = dev.getExpertiseArea();

            java.util.List<main.model.ExpertiseArea> allowed = allowedExpertise(ticketExp);
            if (!allowed.contains(cur)) {
                return OutputBuilder.start("assignTicket", username, timestamp)
                        .error("Developer " + username + " cannot assign ticket " + ticketId
                                + " due to expertise area. Required: "
                                + requiredExpertiseString(ticketExp)
                                + "; Current: " + cur.name() + ".")
                        .build();
            }
        }

        // Seniority check: HIGH/CRITICAL requires MID or SENIOR (matches ref for ticket 4)
        if (dev != null && requiresMidOrSenior(t)) {
            main.model.SeniorityLevel curSen = dev.getSeniorityLevel();
            if (curSen == main.model.SeniorityLevel.JUNIOR) {
                return OutputBuilder.start("assignTicket", username, timestamp)
                        .error("Developer " + username + " cannot assign ticket " + ticketId
                                + " due to seniority level. Required: MID, SENIOR; Current: JUNIOR.")
                        .build();
            }
        }

        // Success: self-assign
        t.setAssignedTo(username);
        t.setAssignedAt(timestamp);
        t.setStatus(main.model.TicketStatus.IN_PROGRESS);
        return null;
    }

    private static boolean requiresMidOrSenior(final main.model.Ticket t) {
        // Task 5 behavior fits: ticket 4 (HIGH) -> requires MID/SENIOR
        return t.getBusinessPriority() == main.model.BusinessPriority.HIGH
                || t.getBusinessPriority() == main.model.BusinessPriority.CRITICAL;
    }

    private static java.util.List<main.model.ExpertiseArea> allowedExpertise(final String ticketExpertise) {
        // Match the required list wording from ref_05:
        // For DB tickets -> BACKEND, DB, FULLSTACK
        if ("DB".equals(ticketExpertise)) {
            return java.util.List.of(main.model.ExpertiseArea.BACKEND,
                    main.model.ExpertiseArea.DB,
                    main.model.ExpertiseArea.FULLSTACK);
        }
        if ("BACKEND".equals(ticketExpertise)) {
            return java.util.List.of(main.model.ExpertiseArea.BACKEND,
                    main.model.ExpertiseArea.FULLSTACK);
        }
        if ("FRONTEND".equals(ticketExpertise)) {
            return java.util.List.of(main.model.ExpertiseArea.FRONTEND,
                    main.model.ExpertiseArea.FULLSTACK);
        }
        if ("DESIGN".equals(ticketExpertise)) {
            return java.util.List.of(main.model.ExpertiseArea.DESIGN,
                    main.model.ExpertiseArea.FULLSTACK);
        }
        // default safe
        return java.util.List.of(main.model.ExpertiseArea.FULLSTACK);
    }

    private static String requiredExpertiseString(final String ticketExpertise) {
        if ("DB".equals(ticketExpertise)) return "BACKEND, DB, FULLSTACK";
        if ("BACKEND".equals(ticketExpertise)) return "BACKEND, FULLSTACK";
        if ("FRONTEND".equals(ticketExpertise)) return "FRONTEND, FULLSTACK";
        if ("DESIGN".equals(ticketExpertise)) return "DESIGN, FULLSTACK";
        return "FULLSTACK";
    }


    private ObjectNode handleViewAssignedTickets(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();

        List<Ticket> assigned = new ArrayList<>();
        for (Ticket t : state.getTickets()) {
            if (username.equals(t.getAssignedTo())) {
                assigned.add(t);
            }
        }

        // Sort like ref: createdAt asc, then businessPriority desc, then id asc
        assigned.sort((a, b) -> {
            int c = a.getCreatedAt().compareTo(b.getCreatedAt());
            if (c != 0) return c;

            int p = Integer.compare(b.getBusinessPriority().ordinal(), a.getBusinessPriority().ordinal());
            if (p != 0) return p;

            return Integer.compare(a.getId(), b.getId());
        });

        return OutputBuilder.start("viewAssignedTickets", username, timestamp)
                .assignedTickets(assigned)
                .build();
    }
    private ObjectNode handleUndoAssignTicket(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();

        final int ticketId = cmdNode.get("ticketID").asInt();

        final Ticket t = state.findTicket(ticketId);
        if (t == null) {
            return OutputBuilder.start("undoAssignTicket", username, timestamp)
                    .error("The ticket " + ticketId + " does not exist.")
                    .build();
        }

        // Only undo if this user is the assignee (safe for later tests)
        if (!username.equals(t.getAssignedTo())) {
            return OutputBuilder.start("undoAssignTicket", username, timestamp)
                    .error("The ticket " + ticketId + " is not assigned to " + username + ".")
                    .build();
        }

        t.setAssignedTo("");
        t.setAssignedAt("");
        t.setStatus(TicketStatus.OPEN);

        return null; // no output on success (matches ref)
    }

}
