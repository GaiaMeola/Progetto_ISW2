package analyzer.model;

import java.util.HashSet;
import java.util.Set;

public class MethodHistoryStats {
    private int methodHistories = 0;
    private int stmtAdded = 0;
    private int stmtDeleted = 0;
    private final Set<String> authors = new HashSet<>();

    public void addEdit(int added, int deleted, String author) {
        this.methodHistories++;
        this.stmtAdded += added;
        this.stmtDeleted += deleted;
        this.authors.add(author);
    }

    public int getMethodHistories() {
        return methodHistories;
    }

    public int getStmtAdded() {
        return stmtAdded;
    }

    public int getStmtDeleted() {
        return stmtDeleted;
    }

    public int getChurn() {
        return stmtAdded + stmtDeleted;
    }

    public int getDistinctAuthors() {
        return authors.size();
    }

}
