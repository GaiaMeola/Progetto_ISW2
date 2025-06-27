package org.example.model;

import org.jetbrains.annotations.NotNull;


public class MethodMetrics {

    /**
     * True if the method is known to be buggy (the classification label).
     */
    private boolean bug = false;

    /**
     * begin line in the class
     */
    private int beginLine;
    /**
     * end line in the class
     */
    private int endLine;
    private String simpleName;

    /**
     * Total churn: sum of all lines of code added and deleted in the method over time.
     */
    private int addedChurn = 0;

    private int removedChurn = 0;

    /**
     * Number of physical (or non-commented) lines of code in the method.
     */

    private int linesOfCode;

    /**
     * Total number of executable statements within the method.
     */
    private int statementCount;

    /**
     * Cyclomatic complexity: the number of independent paths through the method logic.
     */
    private int cyclomaticComplexity;

    /**
     * Cognitive complexity: a measure of how challenging the method is to understand based on control flow and nesting.
     */
    private int cognitiveComplexity;

    /**
     * Maximum nesting depth of control structures (if, for, while, etc.) in the method.
     */
    private int nestingDepth;

    /**
     * Number of parameters the method accepts; high values may indicate high coupling or low cohesion.
     */
    private int parameterCount;

    /**
     * The Amount of code smells detected in the method using static analysis tools.
     */
    private int numberOfCodeSmells;

    private int numberOfTests = 0;
    private int age = 1;
    private int fanIn = 0;
    private int fanOut = 0;
    private String methodAccessor;
    private int numberOfChanges = 0;

    public void addChurn(int addedChurn, int removedChurn) {
        this.addedChurn += addedChurn;
        this.removedChurn += removedChurn;
    }

    public void incChanges() {
        this.numberOfChanges += 1;
    }

    @Override
    public String toString() {
        return "MethodMetrics{" +
                "bug=" + bug +
                ", numberOfFix=" + numberOfChanges +
                ", churn=" + addedChurn +
                ", linesOfCode=" + linesOfCode +
                ", statementCount=" + statementCount +
                ", cyclomaticComplexity=" + cyclomaticComplexity +
                ", cognitiveComplexity=" + cognitiveComplexity +
                ", nestingDepth=" + nestingDepth +
                ", parameterCount=" + parameterCount +
                ", numberOfCodeSmells=" + numberOfCodeSmells +
                ", numberOfReference=" + numberOfTests +
                ", methodAccessor=" + methodAccessor +
                '}';
    }


    private static final String PACKAGE_PRIVATE = "package-private";
    public void setMethodAccessor(@NotNull String accessor) {
        if (accessor.isEmpty()){
            this.methodAccessor = PACKAGE_PRIVATE;
            return;
        }
        this.methodAccessor = accessor;
    }
    public void setLinesOfCode(int linesOfCode) {
        this.linesOfCode = linesOfCode > 0 ?  linesOfCode : 1 ;
    }

    public void incNumberOfTests(){
        this.numberOfTests += 1;
    }
    public void incCodeSmells() {
        this.numberOfCodeSmells++;
    }

    public int getAvgChurn() {
        return this.numberOfChanges > 0 ? this.addedChurn + this.removedChurn / this.numberOfChanges : 0;
    }

    public boolean isBug() {
        return bug;
    }

    public void setBug(boolean bug) {
        this.bug = bug;
    }

    public int getBeginLine() {
        return beginLine;
    }

    public void setBeginLine(int beginLine) {
        this.beginLine = beginLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public int getAddedChurn() {
        return addedChurn;
    }

    public void setAddedChurn(int addedChurn) {
        this.addedChurn = addedChurn;
    }

    public int getRemovedChurn() {
        return removedChurn;
    }

    public void setRemovedChurn(int removedChurn) {
        this.removedChurn = removedChurn;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(int statementCount) {
        this.statementCount = statementCount;
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

    public int getNestingDepth() {
        return nestingDepth;
    }

    public void setNestingDepth(int nestingDepth) {
        this.nestingDepth = nestingDepth;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(int parameterCount) {
        this.parameterCount = parameterCount;
    }

    public int getNumberOfCodeSmells() {
        return numberOfCodeSmells;
    }

    public void setNumberOfCodeSmells(int numberOfCodeSmells) {
        this.numberOfCodeSmells = numberOfCodeSmells;
    }

    public int getNumberOfTests() {
        return numberOfTests;
    }

    public void setNumberOfTests(int numberOfTests) {
        this.numberOfTests = numberOfTests;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getFanIn() {
        return fanIn;
    }

    public void setFanIn(int fanIn) {
        this.fanIn = fanIn;
    }

    public int getFanOut() {
        return fanOut;
    }

    public void setFanOut(int fanOut) {
        this.fanOut = fanOut;
    }

    public String getMethodAccessor() {
        return methodAccessor;
    }

    public int getNumberOfChanges() {
        return numberOfChanges;
    }

    public void setNumberOfChanges(int numberOfChanges) {
        this.numberOfChanges = numberOfChanges;
    }
}