package main.model;

import lombok.Getter;
import lombok.Setter;

public final class Developer extends User {
    private final ExpertiseArea expertiseArea;
    private final SeniorityLevel seniorityLevel;

    @Getter
    private final String hireDate;

    @Getter
    private final String managerUsername;
    @Getter
    @Setter
    private double performanceScore = 0.0;
    public Developer(final String username,
                     final String email,
                     final ExpertiseArea expertiseArea,
                     final SeniorityLevel seniorityLevel,
                     final String hireDate,
                     final String managerUsername) {
        super(username, Role.DEVELOPER);
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
