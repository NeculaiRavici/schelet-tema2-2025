package main.model;

public class User {
    private final String username;
    private final String email;
    private final Role role;

    public User(final String username, final String email, final Role role) {
        this.username = username;
        this.email = email;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public static User fromJson(final com.fasterxml.jackson.databind.JsonNode node) {
        String username = node.get("username").asText();
        String email = node.get("email").asText();
        Role role = Role.valueOf(node.get("role").asText());
        String hireDate = node.has("hireDate") ? node.get("hireDate").asText() : "";
        if (role == Role.MANAGER) {
            java.util.List<String> subs = new java.util.ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode arr = node.get("subordinates");
            if (arr != null && arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode s : arr) {
                    subs.add(s.asText());
                }
            }
            return new Manager(username, email, subs);
        }

        if (role == Role.DEVELOPER) {
            // robust field names
            String expStr = node.has("expertiseArea") ? node.get("expertiseArea").asText() : "FULLSTACK";
            String senStr = node.has("seniorityLevel") ? node.get("seniorityLevel").asText()
                    : (node.has("seniority") ? node.get("seniority").asText() : "MID");

            ExpertiseArea exp = ExpertiseArea.valueOf(expStr);
            SeniorityLevel sen = SeniorityLevel.valueOf(senStr);
            String managerUsername = node.has("managerUsername") ? node.get("managerUsername").asText() : "";
            return new Developer(username, email, exp, sen, hireDate, managerUsername);

        }

        return new User(username, email, role);
    }

}
