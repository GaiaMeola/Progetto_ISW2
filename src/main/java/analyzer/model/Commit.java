package analyzer.model;

import java.time.LocalDateTime;

public class Commit {
    private String id;
    private String message;
    private String author;
    private LocalDateTime date;

    // Getter e setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

}
