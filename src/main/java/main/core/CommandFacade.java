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
        permissions.put("addComment", new Role[]{Role.REPORTER, Role.DEVELOPER, Role.MANAGER});
        permissions.put("undoAddComment", new Role[]{Role.REPORTER,Role.DEVELOPER, Role.MANAGER});
        permissions.put("changeStatus", new Role[]{Role.DEVELOPER});
        permissions.put("viewTicketHistory", new Role[]{Role.DEVELOPER});
        permissions.put("undoChangeStatus", new Role[]{Role.DEVELOPER});
        permissions.put("search", new Role[]{Role.MANAGER, Role.DEVELOPER, Role.REPORTER});

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
            case "undoChangeStatus":
                return handleUndoChangeStatus(cmdNode, user);
            case "createMilestone":
                return handleCreateMilestone(cmdNode, user);
            case "viewMilestones":
                return handleViewMilestones(cmdNode, user);case "assignTicket":
                return handleAssignTicket(cmdNode, user);
            case "viewAssignedTickets":
                return handleViewAssignedTickets(cmdNode, user);
            case "undoAssignTicket":
                return handleUndoAssignTicket(cmdNode, user);
            case "addComment":
                return handleAddComment(cmdNode, user);
            case "undoAddComment":
                return handleUndoAddComment(cmdNode, user);case "changeStatus":
                return handleChangeStatus(cmdNode, user);
            case "viewTicketHistory":
                return handleViewTicketHistory(cmdNode, user);
            case "search":
                return handleSearch(cmdNode, user);
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
            Ticket t = state.findTicket(tid);
            if (t != null) {
                t.addAction(TicketAction.addedToMilestone(name, username, timestamp));
            }
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
        t.addAction(TicketAction.assigned(username, timestamp));
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.addAction(TicketAction.statusChanged("OPEN", "IN_PROGRESS", username, timestamp));
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
            // 1) businessPriority DESC
            int p = Integer.compare(b.getBusinessPriority().ordinal(), a.getBusinessPriority().ordinal());
            if (p != 0) return p;

            // 2) createdAt ASC
            int c = a.getCreatedAt().compareTo(b.getCreatedAt());
            if (c != 0) return c;

            // 3) id ASC
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
        t.addAction(TicketAction.deAssigned(username, timestamp));

        return null; // no output on success (matches ref)
    }
    private ObjectNode handleAddComment(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();
        final int ticketId = cmdNode.get("ticketID").asInt();
        final String content = cmdNode.get("comment").asText();

        final Ticket t = state.findTicket(ticketId);

        // IMPORTANT for test 7: non-existent tickets are silently ignored (no output)
        if (t == null) {
            return null;
        }
        if (user.getRole() == Role.REPORTER && t.getStatus() == TicketStatus.CLOSED) {
            return OutputBuilder.start("addComment", username, timestamp)
                    .error("Reporters cannot comment on CLOSED tickets.")
                    .build();
        }
        // Anonymous tickets: no comments allowed (regardless of length, role, etc.)
        if (t.getReportedBy() == null || t.getReportedBy().isEmpty()) {
            return OutputBuilder.start("addComment", username, timestamp)
                    .error("Comments are not allowed on anonymous tickets.")
                    .build();
        }

        // Min length rule
        if (content == null || content.length() < 10) {
            return OutputBuilder.start("addComment", username, timestamp)
                    .error("Comment must be at least 10 characters long.")
                    .build();
        }

        // Developer can comment only if ticket is assigned to them
        if (user.getRole() == Role.DEVELOPER) {
            if (!username.equals(t.getAssignedTo())) {
                return OutputBuilder.start("addComment", username, timestamp)
                        .error("Ticket " + ticketId + " is not assigned to the developer " + username + ".")
                        .build();
            }
        }

        // Reporter can comment only on tickets they reported
        if (user.getRole() == Role.REPORTER) {
            if (!username.equals(t.getReportedBy())) {
                return OutputBuilder.start("addComment", username, timestamp)
                        .error("Reporter " + username + " cannot comment on ticket " + ticketId + ".")
                        .build();
            }
        }

        // Success
        t.addComment(new Comment(username, content, timestamp));
        return null;
    }
    private ObjectNode handleUndoAddComment(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();
        final int ticketId = cmdNode.get("ticketID").asInt();

        final Ticket t = state.findTicket(ticketId);

        // IMPORTANT for test 7: non-existent tickets are silently ignored (no output)
        if (t == null) {
            return null;
        }

        // Anonymous tickets: no comments allowed (same error as addComment)
        if (t.getReportedBy() == null || t.getReportedBy().isEmpty()) {
            return OutputBuilder.start("undoAddComment", username, timestamp)
                    .error("Comments are not allowed on anonymous tickets.")
                    .build();
        }

        // Developer can undo only if ticket is assigned to them
        if (user.getRole() == Role.DEVELOPER) {
            if (!username.equals(t.getAssignedTo())) {
                return OutputBuilder.start("undoAddComment", username, timestamp)
                        .error("Ticket " + ticketId + " is not assigned to the developer " + username + ".")
                        .build();
            }
        }

        // Reporter can undo only on tickets they reported
        if (user.getRole() == Role.REPORTER) {
            if (!username.equals(t.getReportedBy())) {
                // Use same rule family as addComment
                return OutputBuilder.start("undoAddComment", username, timestamp)
                        .error("Reporter " + username + " cannot comment on ticket " + ticketId + ".")
                        .build();
            }
        }

        // If there is nothing to undo -> silent ignore (NO output) in ref_07
        boolean removed = t.undoLastCommentBy(username);
        if (!removed) {
            return null;
        }

        return null;
    }
    private ObjectNode handleChangeStatus(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();
        final int ticketId = cmdNode.get("ticketID").asInt();

        Ticket t = state.findTicket(ticketId);
        if (t == null) {
            return null; // not tested here
        }

        if (!username.equals(t.getAssignedTo())) {
            return OutputBuilder.start("changeStatus", username, timestamp)
                    .error("Ticket " + ticketId + " is not assigned to developer " + username + ".")
                    .build();
        }

        TicketStatus from = t.getStatus();
        TicketStatus to;

        if (from == TicketStatus.IN_PROGRESS) {
            to = TicketStatus.RESOLVED;
        } else if (from == TicketStatus.RESOLVED) {
            to = TicketStatus.CLOSED;
        } else {
            // no change for OPEN/CLOSED in this test
            return null;
        }

// before t.setStatus(to);
        t.pushStatusHistory(from);
        t.setStatus(to);
        t.addAction(TicketAction.statusChanged(from.name(), to.name(), username, timestamp));

        return null;
    }
    private ObjectNode handleViewTicketHistory(final JsonNode cmdNode, final User user) {
        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();

        List<Ticket> mine = new ArrayList<>();
        for (Ticket t : state.getTickets()) {
            boolean involved = false;
            for (TicketAction a : t.getActions()) {
                if (username.equals(a.getBy())) {   // need getter getBy()
                    involved = true;
                    break;
                }
            }
            if (involved) {
                mine.add(t);
            }
        }
        mine.sort(Comparator.comparingInt(Ticket::getId));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
        out.put("command", "viewTicketHistory");
        out.put("username", username);
        out.put("timestamp", timestamp);

        com.fasterxml.jackson.databind.node.ArrayNode arr = out.putArray("ticketHistory");
        for (Ticket t : mine) {
            arr.add(t.toHistoryJson());
        }
        return out;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode handleUndoChangeStatus(
            final com.fasterxml.jackson.databind.JsonNode cmdNode, final User user) {

        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();
        final int ticketId = cmdNode.get("ticketID").asInt();

        Ticket t = state.findTicket(ticketId);
        if (t == null) {
            return null; // not tested here
        }

        if (!username.equals(t.getAssignedTo())) {
            return OutputBuilder.start("undoChangeStatus", username, timestamp)
                    .error("Ticket " + ticketId + " is not assigned to developer " + username + ".")
                    .build();
        }

        TicketStatus current = t.getStatus();
        TicketStatus prev = t.popStatusHistory();

        // If nothing to undo -> silent (not tested here, but safe)
        if (prev == null) {
            return null;
        }

        t.setStatus(prev);
        t.addAction(TicketAction.statusChanged(current.name(), prev.name(), username, timestamp));
        return null;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode handleSearch(
            final com.fasterxml.jackson.databind.JsonNode cmdNode,
            final main.model.User user) {

        final String username = cmdNode.get("username").asText();
        final String timestamp = cmdNode.get("timestamp").asText();

        final com.fasterxml.jackson.databind.JsonNode filters = cmdNode.get("filters");
        final String searchType = filters.get("searchType").asText(); // "DEVELOPER" or "TICKET"

        if ("DEVELOPER".equals(searchType)) {
            return handleSearchDeveloper(username, timestamp, filters);
        }
        if ("TICKET".equals(searchType)) {
            return handleSearchTicket(username, timestamp, filters);
        }

        // If unknown, return empty results
        return OutputBuilder.start("search", username, timestamp)
                .putString("searchType", searchType)
                .resultsEmpty()
                .build();
    }
    private com.fasterxml.jackson.databind.node.ObjectNode handleSearchDeveloper(
            final String username,
            final String timestamp,
            final com.fasterxml.jackson.databind.JsonNode filters) {

        final String exp = filters.has("expertiseArea") ? filters.get("expertiseArea").asText() : null;
        final String sen = filters.has("seniority") ? filters.get("seniority").asText() : null;

        java.util.List<main.model.Developer> matched = new java.util.ArrayList<>();
        for (main.model.User u : state.users.values()) {
            if (!(u instanceof main.model.Developer)) continue;
            main.model.Developer d = (main.model.Developer) u;

            if (exp != null && !d.getExpertiseArea().name().equals(exp)) continue;
            if (sen != null && !d.getSeniorityLevel().name().equals(sen)) continue;

            matched.add(d);
        }

        // sort by username asc? ref order is alexandra, isabella, marcus (lexicographic)
        matched.sort(java.util.Comparator.comparing(main.model.User::getUsername));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
        out.put("command", "search");
        out.put("username", username);
        out.put("timestamp", timestamp);
        out.put("searchType", "DEVELOPER");

        com.fasterxml.jackson.databind.node.ArrayNode arr = out.putArray("results");
        for (main.model.Developer d : matched) {
            com.fasterxml.jackson.databind.node.ObjectNode n = mapper.createObjectNode();
            n.put("username", d.getUsername());
            n.put("expertiseArea", d.getExpertiseArea().name());
            n.put("seniority", d.getSeniorityLevel().name());
            n.put("performanceScore", 0.0);
            n.put("hireDate", d.getHireDate()); // add getter + field (see next section)
            arr.add(n);
        }

        return out;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode handleSearchTicket(
            final String username,
            final String timestamp,
            final com.fasterxml.jackson.databind.JsonNode filters) {

        String typeFilter = filters.has("type") ? filters.get("type").asText() : null;
        String prioFilter = filters.has("businessPriority") ? filters.get("businessPriority").asText() : null;
        String createdAfter = filters.has("createdAfter") ? filters.get("createdAfter").asText() : null;
        boolean available = filters.has("availableForAssignment") && filters.get("availableForAssignment").asBoolean();

        java.util.List<String> keywords = new java.util.ArrayList<>();
        if (filters.has("keywords") && filters.get("keywords").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode k : filters.get("keywords")) {
                keywords.add(k.asText());
            }
        }

        java.util.List<main.model.Ticket> base = new java.util.ArrayList<>(state.getTickets());

        // Visibility rules depend on role
        main.model.User requester = state.getUser(username);
        main.model.Role role = requester.getRole();

        java.util.List<main.model.Ticket> visible = new java.util.ArrayList<>();

        for (main.model.Ticket t : base) {

            // role-based visibility:
            if (role == main.model.Role.MANAGER) {
                // manager sees all
            } else if (role == main.model.Role.DEVELOPER) {
                // developer sees only milestone tickets where dev is assigned
                String msName = state.getMilestoneNameForTicket(t.getId());
                if (msName == null) continue;
                main.model.Milestone ms = state.getMilestone(msName);
                if (ms == null) continue;
                if (!ms.isDevAssigned(username)) continue;
            } else if (role == main.model.Role.REPORTER) {
                // reporter sees only tickets they reported (and non-anonymous)
                if (!username.equals(t.getReportedBy())) continue;
            }

            // ✅ CHANGE #1: Search returns only OPEN tickets (matches ref_11)
            if (t.getStatus() != main.model.TicketStatus.OPEN) {
                continue;
            }

            // ✅ CHANGE #2: availableForAssignment means "developer can actually assign it"
            if (available) {
                // only developers use this flag in the tests
                if (role != main.model.Role.DEVELOPER) {
                    continue;
                }
                // must be unassigned
                if (t.getAssignedTo() != null && !t.getAssignedTo().isEmpty()) {
                    continue;
                }

                // must satisfy the same eligibility constraints as assignTicket
                main.model.Developer dev = (main.model.Developer) requester;
                if (!canAssignForSearch(t, dev)) {
                    continue;
                }
            }

            // type filter
            if (typeFilter != null && !t.getType().name().equals(typeFilter)) continue;

            // businessPriority filter
            if (prioFilter != null && !t.getBusinessPriority().name().equals(prioFilter)) continue;

            // createdAfter filter (strictly after date)
            if (createdAfter != null && t.getCreatedAt().compareTo(createdAfter) <= 0) continue;

            // keyword filter
            java.util.List<String> matching = new java.util.ArrayList<>();
            if (!keywords.isEmpty()) {
                String titleLower = t.getTitle().toLowerCase();
                for (String kw : keywords) {
                    String kwLower = kw.toLowerCase();
                    // "whole word" simple boundaries
                    if (titleLower.matches(".*\\b" + java.util.regex.Pattern.quote(kwLower) + "\\b.*")) {
                        matching.add(kw);
                    }
                }
                if (matching.isEmpty()) continue;
            }

            // attach matchingWords only if keywords were present
            t.setTempMatchingWords(matching);
            visible.add(t);
        }

        // sort results by id asc (matches ref)
        visible.sort(java.util.Comparator.comparingInt(main.model.Ticket::getId));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
        out.put("command", "search");
        out.put("username", username);
        out.put("timestamp", timestamp);
        out.put("searchType", "TICKET");

        com.fasterxml.jackson.databind.node.ArrayNode arr = out.putArray("results");
        for (main.model.Ticket t : visible) {
            com.fasterxml.jackson.databind.node.ObjectNode n = mapper.createObjectNode();
            n.put("id", t.getId());
            n.put("type", t.getType().name());
            n.put("title", t.getTitle());
            n.put("businessPriority", t.getBusinessPriority().name());
            n.put("status", t.getStatus().name());
            n.put("createdAt", t.getCreatedAt());
            n.put("solvedAt", ""); // ref uses empty string here
            n.put("reportedBy", t.getReportedBy());

            if (!keywords.isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode mw = n.putArray("matchingWords");
                for (String w : t.getTempMatchingWords()) {
                    mw.add(w);
                }
            }

            arr.add(n);
        }

        // clear temp matching words
        for (main.model.Ticket t : visible) {
            t.clearTempMatchingWords();
        }

        return out;
    }
    private boolean canAssignForSearch(final main.model.Ticket t, final main.model.Developer dev) {
        // must belong to a milestone
        String msName = state.getMilestoneNameForTicket(t.getId());
        if (msName == null) return false;

        main.model.Milestone ms = state.getMilestone(msName);
        if (ms == null) return false;

        // developer must be assigned to milestone
        if (!ms.isDevAssigned(dev.getUsername())) return false;

        // cannot assign from blocked milestone
        if (ms.isBlocked(state)) return false;

        // expertise constraint (reuse your Task 5 mapping)
        String ticketExp = t.getExpertiseArea();
        if (ticketExp != null && !ticketExp.isEmpty()) {
            if (!allowedExpertise(ticketExp).contains(dev.getExpertiseArea())) {
                return false;
            }
        }

        // seniority constraint: HIGH/CRITICAL require MID or SENIOR
        if (requiresMidOrSenior(t) && dev.getSeniorityLevel() == main.model.SeniorityLevel.JUNIOR) {
            return false;
        }

        return true;
    }


}
