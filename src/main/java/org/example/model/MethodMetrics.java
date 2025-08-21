package org.example.model;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Rappresenta le metriche associate a un singolo metodo di una classe.
 */
public class MethodMetrics {

    // === Etichetta del bug ===
    /** True se il metodo è noto per avere bug (label di classificazione) */
    private boolean bug = false;

    // === Linee nel file ===
    /** Riga iniziale del metodo nella classe */
    private int beginLine;
    /** Riga finale del metodo nella classe */
    private int endLine;

    /** Nome semplice del metodo */
    private String simpleName;

    // === Churn ===
    /** Somma delle righe aggiunte nel tempo */
    private int addedChurn = 0;
    /** Somma delle righe rimosse nel tempo */
    private int removedChurn = 0;
    /** Massimo aggiunto in una singola revisione */
    private int maxAddedChurn = 0;
    /** Massimo rimosso in una singola revisione */
    private int maxRemovedChurn = 0;

    // === Complessità e codice ===
    /** Linee di codice effettive nel metodo (escludendo commenti vuoti) */
    private int linesOfCode;
    /** Numero di statement eseguibili */
    private int statementCount;
    /** Complessità ciclomatica (percorsi indipendenti nel flusso) */
    private int cyclomaticComplexity;
    /** Complessità cognitiva (difficoltà di comprensione del metodo) */
    private int cognitiveComplexity;
    /** Profondità massima di nidificazione di if/for/while/etc. */
    private int nestingDepth;
    /** Numero di parametri del metodo */
    private int parameterCount;

    // === Altri indicatori ===
    /** Halstead Effort: misura la complessità computazionale del metodo */
    private double halsteadEffort;
    /** Densità dei commenti (0-1) */
    private double commentDensity;
    /** Numero di code smells rilevati */
    private int numberOfCodeSmells;
    /** Età del metodo (numero di revisioni) */
    private int age = 1;
    /** Numero di chiamate al metodo da altri metodi (fan-in) */
    private int fanIn = 0;
    /** Numero di chiamate che il metodo fa ad altri metodi (fan-out) */
    private int fanOut = 0;
    /** Tipo di accesso al metodo (public/private/etc.) */
    private String methodAccessor;
    /** Numero di modifiche effettuate */
    private int numberOfChanges = 0;
    /** Numero di test associati al metodo */
    private int numberOfTests = 0;

    /** Autori che hanno modificato il metodo */
    private final Set<String> authors = new HashSet<>();

    private static final String PACKAGE_PRIVATE = "package-private";

    // === Metodi utilità ===
    public void addChurn(int addedChurn, int removedChurn) {
        this.addedChurn += addedChurn;
        this.removedChurn += removedChurn;
        if (addedChurn > maxAddedChurn) maxAddedChurn = addedChurn;
        if (removedChurn > maxRemovedChurn) maxRemovedChurn = removedChurn;
    }

    public void incChanges() {
        this.numberOfChanges++;
    }

    public void incCodeSmells() {
        this.numberOfCodeSmells++;
    }

    public void addAuthor(String name) {
        if (name != null && !name.isEmpty()) this.authors.add(name);
    }

    public int getAuthorCount() {
        return this.authors.size(); // restituisce 0 se non ci sono autori
    }

    public void setMethodAccessor(@NotNull String accessor) {
        if (accessor.isEmpty()) {
            this.methodAccessor = PACKAGE_PRIVATE;
        } else {
            this.methodAccessor = accessor;
        }
    }

    public void setLinesOfCode(int linesOfCode) {
        this.linesOfCode = Math.max(linesOfCode, 0);
    }

    // === Getter/Setter standard ===
    public boolean isBug() { return bug; }
    public void setBug(boolean bug) { this.bug = bug; }

    public int getBeginLine() { return beginLine; }
    public void setBeginLine(int beginLine) { this.beginLine = beginLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }

    public String getSimpleName() { return simpleName; }
    public void setSimpleName(String simpleName) { this.simpleName = simpleName; }

    public int getAddedChurn() { return addedChurn; }
    public int getRemovedChurn() { return removedChurn; }
    public int getMaxAddedChurn() { return maxAddedChurn; }
    public int getMaxRemovedChurn() { return maxRemovedChurn; }

    public int getLinesOfCode() { return linesOfCode; }
    public int getStatementCount() { return statementCount; }
    public void setStatementCount(int statementCount) { this.statementCount = statementCount; }

    public int getCyclomaticComplexity() { return cyclomaticComplexity; }
    public void setCyclomaticComplexity(int cyclomaticComplexity) { this.cyclomaticComplexity = cyclomaticComplexity; }

    public int getCognitiveComplexity() { return cognitiveComplexity; }
    public void setCognitiveComplexity(int cognitiveComplexity) { this.cognitiveComplexity = cognitiveComplexity; }

    public int getNestingDepth() { return nestingDepth; }
    public void setNestingDepth(int nestingDepth) { this.nestingDepth = nestingDepth; }

    public int getParameterCount() { return parameterCount; }
    public void setParameterCount(int parameterCount) { this.parameterCount = parameterCount; }

    public double getHalsteadEffort() { return halsteadEffort; }
    public void setHalsteadEffort(double halsteadEffort) { this.halsteadEffort = halsteadEffort; }

    public double getCommentDensity() { return commentDensity; }
    public void setCommentDensity(double commentDensity) { this.commentDensity = commentDensity; }

    public int getNumberOfCodeSmells() { return numberOfCodeSmells; }
    public void setNumberOfCodeSmells(int numberOfCodeSmells) { this.numberOfCodeSmells = numberOfCodeSmells; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public int getFanIn() { return fanIn; }
    public void setFanIn(int fanIn) { this.fanIn = fanIn; }

    public int getFanOut() { return fanOut; }
    public void setFanOut(int fanOut) { this.fanOut = fanOut; }

    public String getMethodAccessor() { return methodAccessor; }

    public int getNumberOfChanges() { return numberOfChanges; }
    public void setNumberOfChanges(int numberOfChanges) { this.numberOfChanges = numberOfChanges; }

    public Set<String> getAuthors() { return authors; }

    public int getNumberOfTests() {
        return numberOfTests;
    }

    public void setNumberOfTests(int numberOfTests) {
        this.numberOfTests = numberOfTests;
    }

    /** Imposta direttamente il churn aggiunto (utile se vuoi sovrascrivere il valore) */
    public void setAddedChurn(int addedChurn) {
        this.addedChurn = addedChurn;
        if (addedChurn > maxAddedChurn) maxAddedChurn = addedChurn;
    }

    /** Imposta direttamente il churn rimosso (utile se vuoi sovrascrivere il valore) */
    public void setRemovedChurn(int removedChurn) {
        this.removedChurn = removedChurn;
        if (removedChurn > maxRemovedChurn) maxRemovedChurn = removedChurn;
    }

    /** Restituisce il churn medio (media tra aggiunto e rimosso) */
    public double getAvgChurn() {
        return (addedChurn + removedChurn) / 2.0;
    }

    // === String rappresentativa ===
    @Override
    public String toString() {
        return "MethodMetrics{" +
                "bug=" + bug +
                ", numberOfChanges=" + numberOfChanges +
                ", churn=" + addedChurn +
                ", linesOfCode=" + linesOfCode +
                ", cyclomaticComplexity=" + cyclomaticComplexity +
                ", cognitiveComplexity=" + cognitiveComplexity +
                ", nestingDepth=" + nestingDepth +
                ", parameterCount=" + parameterCount +
                ", numberOfCodeSmells=" + numberOfCodeSmells +
                ", methodAccessor='" + methodAccessor + '\'' +
                ", startLine=" + beginLine +
                ", endLine=" + endLine +
                '}';
    }
}