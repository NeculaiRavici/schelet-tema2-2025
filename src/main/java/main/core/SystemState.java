package main.core;

import lombok.Getter;
import lombok.Setter;
import main.model.Milestone;
import main.model.Ticket;
import main.model.User;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class SystemState {
    private static final SystemState INSTANCE = new SystemState();
    @Getter
    public final Map<String, User> users = new HashMap<>();
    public final List<Ticket> tickets = new ArrayList<>();

    // --- Milestones ---
    private final Map<String, Milestone> milestonesByName = new HashMap<>();
    private final Map<Integer, String> ticketToMilestone = new HashMap<>();

    private int nextTicketId = 0;
    public boolean stopped = false;

    // --- Testing phase tracking (12 days) ---
    private static final int TESTING_DAYS = 12;
    private LocalDate testingStartDate = null;

    private final Map<String, List<String>> notifications = new HashMap<>();
    private final Map<String, String> lastNotificationDate = new HashMap<>();

    private SystemState() {
    }

    public static SystemState getInstance() {
        return INSTANCE;
    }

    public void addUser(final User user) {
        users.put(user.getUsername(), user);
    }

    public User getUser(final String username) {
        return users.get(username);
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public int allocateTicketId() {
        int id = nextTicketId;
        nextTicketId++;
        return id;
    }

    public boolean isTestingPhase(final String timestamp) {
        LocalDate current = LocalDate.parse(timestamp);
        if (testingStartDate == null) {
            testingStartDate = current;
        }
        long days = ChronoUnit.DAYS.between(testingStartDate, current);
        return days < TESTING_DAYS;
    }

    public void startTestingPhaseFrom(final String timestamp) {
        testingStartDate = LocalDate.parse(timestamp);
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stop() {
        this.stopped = true;
    }

    public Ticket findTicket(final int id) {
        for (Ticket t : tickets) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }

    // --- Milestone storage helpers ---
    public void addMilestone(final Milestone m) {
        milestonesByName.put(m.getName(), m);
    }

    public Milestone getMilestone(final String name) {
        return milestonesByName.get(name);
    }

    public java.util.Collection<main.model.Milestone> getAllMilestones() {
        return milestonesByName.values();
    }

    public String getMilestoneNameForTicket(final int ticketId) {
        return ticketToMilestone.get(ticketId);
    }

    public void linkTicketToMilestone(final int ticketId, final String milestoneName) {
        ticketToMilestone.put(ticketId, milestoneName);
    }
    public void reset() {
        users.clear();
        tickets.clear();

        milestonesByName.clear();
         ticketToMilestone.clear();

        notifications.clear();
        lastNotificationDate.clear();

        nextTicketId = 0;
        stopped = false;

         testingStartDate = null;
    }
    public void pushNotification(final String username, final String message) {
        notifications.computeIfAbsent(username, k -> new ArrayList<>()).add(message);
    }

    public List<String> consumeNotifications(final String username) {
        List<String> list = notifications.get(username);
        if (list == null) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>(list);
        list.clear(); // consume
        return out;
    }

    public boolean markOnce(final String key, final String dateIso) {
        if (lastNotificationDate.containsKey(key)) {
            return false;
        }
        lastNotificationDate.put(key, dateIso);
        return true;
    }
    public boolean silentMode = false;
    public void setSilentMode(boolean v) { silentMode = v; }
    public boolean isSilentMode() { return silentMode; }

}
