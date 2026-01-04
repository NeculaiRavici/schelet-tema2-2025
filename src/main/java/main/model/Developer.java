package main.model;

public final class Developer extends User {
    private final ExpertiseArea expertiseArea;
    private final SeniorityLevel seniorityLevel;

    public Developer(final String username,
                     final String email,
                     final ExpertiseArea expertiseArea,
                     final SeniorityLevel seniorityLevel) {
        super(username, email, Role.DEVELOPER);
        this.expertiseArea = expertiseArea;
        this.seniorityLevel = seniorityLevel;
    }

    public ExpertiseArea getExpertiseArea() {
        return expertiseArea;
    }

    public SeniorityLevel getSeniorityLevel() {
        return seniorityLevel;
    }
}
