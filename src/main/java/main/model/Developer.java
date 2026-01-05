package main.model;

import lombok.Getter;

public final class Developer extends User {
    private final ExpertiseArea expertiseArea;
    private final SeniorityLevel seniorityLevel;
    @Getter
    private final String hireDate;
    public Developer(final String username,
                     final String email,
                     final ExpertiseArea expertiseArea,
                     final SeniorityLevel seniorityLevel,
                     final String hireDate) {
        super(username, email, Role.DEVELOPER);
        this.expertiseArea = expertiseArea;
        this.seniorityLevel = seniorityLevel;
        this.hireDate = hireDate;
    }

    public ExpertiseArea getExpertiseArea() {
        return expertiseArea;
    }

    public SeniorityLevel getSeniorityLevel() {
        return seniorityLevel;
    }
    }

