package main.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;

public class Comment {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Getter
    private final String author;
    @Getter
    private final String content;
    @Getter
    private final String createdAt;

    public Comment(final String author, final String content, final String createdAt) {
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
    }

    public ObjectNode toJson() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("author", author);
        n.put("content", content);
        n.put("createdAt", createdAt);
        return n;
    }

}
