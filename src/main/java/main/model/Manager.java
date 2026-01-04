package main.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Manager extends User {
    private final List<String> subordinates;

    public Manager(final String username, final String email, final List<String> subordinates) {
        super(username, email, Role.MANAGER);
        this.subordinates = new ArrayList<>(subordinates);
    }

    public List<String> getSubordinates() {
        return Collections.unmodifiableList(subordinates);
    }

    public boolean manages(final String devUsername) {
        return subordinates.contains(devUsername);
    }
}
