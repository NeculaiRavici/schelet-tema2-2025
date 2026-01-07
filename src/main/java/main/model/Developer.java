package main.model;

import lombok.Getter;

public final class Developer extends User {
    private final ExpertiseArea expertiseArea;
    private final SeniorityLevel seniorityLevel;

    @Getter
    private final String hireDate;

    @Getter
    private final String managerUsername;

    public Developer(final String username,
                     final String email,
                     final ExpertiseArea expertiseArea,
                     final SeniorityLevel seniorityLevel,
                     final String hireDate,
                     final String managerUsername) {
        super(username, email, Role.DEVELOPER);
        this.expertiseArea = expertiseArea;
        this.seniorityLevel = seniorityLevel;
        this.hireDate = hireDate;
        this.managerUsername = managerUsername;
    }

    public ExpertiseArea getExpertiseArea() {
        return expertiseArea;
    }

    public SeniorityLevel getSeniorityLevel() {
        return seniorityLevel;
    }
}
