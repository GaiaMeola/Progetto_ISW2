package analyzer.model;

import java.time.LocalDate;

public class Release {
    private String id;
    private String name;
    private LocalDate releaseDate;
    private boolean released;

    public Release(String name, LocalDate date) {
        this.name = name;
        this.releaseDate = date;
    }

    public Release() {

    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    @Override
    public String toString() {
        return name + " (" + releaseDate + ")";
    }
}

