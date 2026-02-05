package analyzer.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    private String projectName;
    private String methodName;
    private String releaseId;
    private int loc;
    private int cyclomaticComplexity;
    private int cognitiveComplexity;
    private int numberOfSmells;
    private int parameterCount;
    private int nestingDepth;
    private int methodHistories;
    private int stmtAdded;
    private int stmtDeleted;
    private int churn;
    private boolean bugginess;
    private String methodCode;
    private List<String> detectedSmells = new ArrayList<>();
    private int startLine;
    private int endLine;
    private LocalDate releaseDate;
    private int statementCount;
    private int distinctAuthors;
    private int returnTypeComplexity;
    private int localVariableCount;

    public MethodInfo() {
        // Basic constructor
    }

    public int getStatementCount() { return statementCount; }
    public void setStatementCount(int statementCount) { this.statementCount = statementCount; }

    public int getDistinctAuthors() { return distinctAuthors; }
    public void setDistinctAuthors(int distinctAuthors) { this.distinctAuthors = distinctAuthors; }

    public int getReturnTypeComplexity() { return returnTypeComplexity; }
    public void setReturnTypeComplexity(int returnTypeComplexity) { this.returnTypeComplexity = returnTypeComplexity; }

    public int getLocalVariableCount() { return localVariableCount; }
    public void setLocalVariableCount(int localVariableCount) { this.localVariableCount = localVariableCount; }

    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public String getMethodCode() { return methodCode; }

    public void setMethodCode(String methodCode) { this.methodCode = methodCode; }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public int getLoc() {
        return loc;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }

    public int getCognitiveComplexity() {
        return cognitiveComplexity;
    }

    public void setCognitiveComplexity(int cognitiveComplexity) {
        this.cognitiveComplexity = cognitiveComplexity;
    }

    public int getNumberOfSmells() {
        return numberOfSmells;
    }

    public void setNumberOfSmells(int numberOfSmells) {
        this.numberOfSmells = numberOfSmells;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(int parameterCount) {
        this.parameterCount = parameterCount;
    }

    public int getNestingDepth() {
        return nestingDepth;
    }

    public void setNestingDepth(int nestingDepth) {
        this.nestingDepth = nestingDepth;
    }

    public int getMethodHistories() {
        return methodHistories;
    }

    public void setMethodHistories(int methodHistories) {
        this.methodHistories = methodHistories;
    }

    public int getStmtAdded() {
        return stmtAdded;
    }

    public void setStmtAdded(int stmtAdded) {
        this.stmtAdded = stmtAdded;
    }

    public int getStmtDeleted() {
        return stmtDeleted;
    }

    public void setStmtDeleted(int stmtDeleted) {
        this.stmtDeleted = stmtDeleted;
    }

    public int getChurn() {
        return churn;
    }

    public void setChurn(int churn) {
        this.churn = churn;
    }

    public boolean isBugginess() {
        return bugginess;
    }

    public void setBugginess(boolean bugginess) {
        this.bugginess = bugginess;
    }

    public List<String> getDetectedSmells() { return detectedSmells; }

    public void setDetectedSmells(List<String> detectedSmells) { this.detectedSmells = detectedSmells; }

    public int getStartLine() { return startLine;}

    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }

    public void setEndLine(int endLine) { this.endLine = endLine; }

}

