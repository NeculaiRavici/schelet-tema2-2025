package main.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.core.SystemState;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Milestone {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final List<String> blockingFor;
    private final String dueDate;   // "YYYY-MM-DD"
    private final String createdAt; // timestamp of createMilestone
    private final List<Integer> tickets;
    private final List<String> assignedDevs;
    private final String createdBy;

    public Milestone(final String name,
                     final List<String> blockingFor,
                     final String dueDate,
                     final String createdAt,
                     final List<Integer> tickets,
                     final List<String> assignedDevs,
                     final String createdBy) {
        this.name = name;
        this.blockingFor = new ArrayList<>(blockingFor);
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.tickets = new ArrayList<>(tickets);
        this.assignedDevs = new ArrayList<>(assignedDevs);
        this.createdBy = createdBy;
    }

    public String getName() {
        return name;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public List<String> getAssignedDevs() {
        return Collections.unmodifiableList(assignedDevs);
    }

    public boolean isDevAssigned(final String devUsername) {
        return assignedDevs.contains(devUsername);
    }

    public List<String> getBlockingFor() {
        return new ArrayList<>(blockingFor);
    }

    public boolean isActive(final SystemState state) {
        for (int id : tickets) {
            Ticket t = state.findTicket(id);
            if (t != null && t.getStatus() != TicketStatus.CLOSED) {
                return true;
            }
        }
        return false;
    }

    /**
     * A milestone is blocked if there exists an ACTIVE milestone that lists this milestone in its "blockingFor".
     * Example from ref: Release 1.0 blockingFor includes UI-Overhaul, and Release 1.0 is ACTIVE => UI-Overhaul isBlocked true.
     */
    public boolean isBlocked(final SystemState state) {
        for (Milestone other : state.getAllMilestones()) {
            if (other == this) {
                continue;
            }
            if (other.isActive(state) && other.blockingFor.contains(this.name)) {
                return true;
            }
        }
        return false;
    }

    private int daysUntilDue(final LocalDate now, final SystemState state) {
        LocalDate due = LocalDate.parse(dueDate);

        // For completed milestones, use completion date instead of now
        if (!isActive(state)) {
            LocalDate completedAt = findCompletionDate(state);
            if (completedAt != null) {
                if (due.isBefore(completedAt)) {
                    return 0;
                }
                long diff = ChronoUnit.DAYS.between(completedAt, due);
                return (int) diff + 1;
            }
        }

        if (due.isBefore(now)) {
            return 0;
        }
        // Inclusive-style counting
        long diff = ChronoUnit.DAYS.between(now, due);
        return (int) diff + 1;
    }

    private int overdueBy(final java.time.LocalDate now,
                          final boolean isCompleted,
                          final SystemState state) {
        java.time.LocalDate due = java.time.LocalDate.parse(dueDate);

        // For completed milestones, calculate based on completion date
        if (isCompleted) {
            LocalDate completedAt = findCompletionDate(state);
            if (completedAt != null && completedAt.isAfter(due)) {
                long diff = java.time.temporal.ChronoUnit.DAYS.between(due, completedAt);
                return (int) diff + 1;  // Inclusive counting
            }
            return 0;  // Completed on time
        }

        if (!now.isAfter(due)) {
            return 0;
        }
        long diff = java.time.temporal.ChronoUnit.DAYS.between(due, now);
        return (int) diff + 1;
    }

    /**
     * Find the date when this milestone was completed (all tickets closed).
     */
    private LocalDate findCompletionDate(final SystemState state) {
        LocalDate latestClose = null;
        for (int tid : tickets) {
            Ticket t = state.findTicket(tid);
            if (t == null || t.getStatus() != TicketStatus.CLOSED) {
                return null;  // Not all closed yet
            }
            // Find when this ticket was closed
            String closedAt = findClosedAt(t);
            if (closedAt != null && !closedAt.isEmpty()) {
                LocalDate closeDate = LocalDate.parse(closedAt);
                if (latestClose == null || closeDate.isAfter(latestClose)) {
                    latestClose = closeDate;
                }
            }
        }
        return latestClose;
    }

    private static String findClosedAt(final Ticket t) {
        for (TicketAction a : t.getActions()) {
            if ("STATUS_CHANGED".equals(a.getAction()) && "CLOSED".equals(a.getTo())) {
                return a.getTimestamp();
            }
        }
        return "";
    }

    private List<Integer> openTickets(final SystemState state) {
        List<Integer> out = new ArrayList<>();
        for (int id : tickets) {
            Ticket t = state.findTicket(id);
            if (t != null && t.getStatus() != TicketStatus.CLOSED) {
                out.add(id);
            }
        }
        return out;
    }

    private List<Integer> closedTickets(final SystemState state) {
        List<Integer> out = new ArrayList<>();
        for (int id : tickets) {
            Ticket t = state.findTicket(id);
            if (t != null && t.getStatus() == TicketStatus.CLOSED) {
                out.add(id);
            }
        }
        return out;
    }
    private double completionPercentage(final SystemState state) {
        if (tickets.isEmpty()) {
            return 0.0;
        }
        int closed = closedTickets(state).size();
        double frac = closed / (double) tickets.size(); // fraction, not percent
        return Math.round(frac * 100.0) / 100.0;        // 2 decimals
    }

    public ObjectNode toOutputJson(final SystemState state, final String timestamp) {
        LocalDate now = LocalDate.parse(timestamp);

        ObjectNode n = MAPPER.createObjectNode();

        // Field order matches ref output
        n.put("name", name);

        ArrayNode bf = n.putArray("blockingFor");
        for (String s : blockingFor) {
            bf.add(s);
        }

        n.put("dueDate", dueDate);
        n.put("createdAt", createdAt);

        ArrayNode tArr = n.putArray("tickets");
        for (int id : tickets) {
            tArr.add(id);
        }

        ArrayNode devArr = n.putArray("assignedDevs");
        for (String d : assignedDevs) {
            devArr.add(d);
        }

        n.put("createdBy", createdBy);

        boolean activeNow = isActive(state);
        boolean completed = !activeNow;

        String status = completed ? "COMPLETED" : "ACTIVE";
        n.put("status", status);



        n.put("isBlocked", isBlocked(state));

        n.put("daysUntilDue", daysUntilDue(now, state));
        n.put("overdueBy", overdueBy(now, completed, state));

        List<Integer> open = openTickets(state);
        List<Integer> closed = closedTickets(state);

        ArrayNode openArr = n.putArray("openTickets");
        for (int id : open) {
            openArr.add(id);
        }

        ArrayNode closedArr = n.putArray("closedTickets");
        for (int id : closed) {
            closedArr.add(id);
        }

        n.put("completionPercentage", completionPercentage(state));

        // Build repartition with assigned tickets count for sorting
        java.util.List<java.util.Map.Entry<String, java.util.List<Integer>>> repartitionList = new java.util.ArrayList<>();
        for (String dev : assignedDevs) {
            java.util.List<Integer> devTickets = new java.util.ArrayList<>();
            for (int tid : tickets) {
                Ticket tt = state.findTicket(tid);
                if (tt != null && dev.equals(tt.getAssignedTo())) {
                    devTickets.add(tid);
                }
            }
            repartitionList.add(new java.util.AbstractMap.SimpleEntry<>(dev, devTickets));
        }
        
        // Sort: by number of assigned tickets ascending, then by developer name alphabetically
        repartitionList.sort((a, b) -> {
            int cmp = Integer.compare(a.getValue().size(), b.getValue().size());
            if (cmp != 0) return cmp;
            return a.getKey().compareTo(b.getKey());
        });

        ArrayNode rep = n.putArray("repartition");
        for (java.util.Map.Entry<String, java.util.List<Integer>> entry : repartitionList) {
            ObjectNode r = MAPPER.createObjectNode();
            r.put("developer", entry.getKey());

            ArrayNode assigned = r.putArray("assignedTickets");
            for (int tid : entry.getValue()) {
                assigned.add(tid);
            }
            rep.add(r);
        }

        return n;
    }
    public java.util.List<Integer> getTickets() { return tickets; }
    private boolean wasEverBlocked = false;

    public void updateBlockedHistory(final SystemState state) {
        if (isBlocked(state)) {
            wasEverBlocked = true;
        }
    }

    /**
     * Check if this milestone was ever blocked by any other milestone.
     * This includes milestones that have since been completed.
     */
    public boolean wasEverBlocked() {
        return wasEverBlocked;
    }
    
    /**
     * Check if any other milestone ever listed this milestone in its blockingFor.
     * This is independent of whether that milestone is still active.
     */
    public boolean hasBlockingMilestones(final SystemState state) {
        for (Milestone other : state.getAllMilestones()) {
            if (other == this) {
                continue;
            }
            // If any milestone ever listed us in blockingFor, we were blocked at some point
            if (other.blockingFor.contains(this.name)) {
                return true;
            }
        }
        return false;
    }

}
