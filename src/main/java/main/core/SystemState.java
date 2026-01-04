package main.core;

import main.model.Ticket;
import main.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SystemState {
    private static final SystemState INSTANCE = new SystemState();
    public final Map<String, User> users=new HashMap<>();
    public final List<Ticket> tickets=new ArrayList<>();
    public  int nextTicketId=0;
    public boolean testingPhase=true;
    public boolean stopped=false;
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

    public boolean isTestingPhase() {
        return testingPhase;
    }

    public void setTestingPhase(final boolean testingPhase) {
        this.testingPhase = testingPhase;
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
}
