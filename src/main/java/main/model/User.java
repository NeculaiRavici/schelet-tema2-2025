package main.model;


import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    @Getter
    private final String username;
    @Getter
    private final String email;
    @Getter
    private final Role role;
    public User(final String username, final String email, final Role role) {
        this.username = username;
        this.email = email;
        this.role = role;
    }
    public static User fromJSON(final JsonNode userNode) {
        String username = userNode.get("username").asText();
        String email = userNode.get("email").asText();
        return new User(username, email, Role.valueOf(userNode.get("role").asText()));
    }
}
