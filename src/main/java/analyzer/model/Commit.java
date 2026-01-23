package analyzer.model;

import java.time.LocalDateTime;
import java.util.List;

public class Commit {
    private String id;
    private String message;
    private String author;
    private LocalDateTime date;
    private List<String> filesTouched;

    // Getter e setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public List<String> getFilesTouched() { return filesTouched; }
    public void setFilesTouched(List<String> filesTouched) { this.filesTouched = filesTouched; }
}
