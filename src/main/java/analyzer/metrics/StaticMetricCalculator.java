package analyzer.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;

// Questa classe calcola le metriche statiche per un emtodo Java
public class StaticMetricCalculator {


    /*
     Conta le righe effettive di codice, escludendo:
     - parentesi graffe isolate
     - righe vuote
     - commenti inline (//...)
     */
    public int calculateLoc(MethodDeclaration method) {
        String[] lines = method.toString().split("\\r?\\n");
        int loc = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}") && !trimmed.startsWith("//")) {
                loc++;
            }
        }
        return loc;
    }

    /*
    Calcola la complessita ciclomatica, parte da 1 e aggiunge:
    - if, for, foreach, while, do
    - case nei switch
    - catch
    - ternari (ConditionalExpr)
     */
    public int calculateCyclomaticComplexity(MethodDeclaration method) {
        int complexity = 1;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(SwitchEntry.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();
        return complexity;
    }

    /*
    Calcola la complessita cognitiva, parte da zero e:
    - somma i costrutti logici come sopra,
    - penalizza la profondità del nesting (solo se maggiore di 1)
     */
    public int calculateCognitiveComplexity(MethodDeclaration method) {
        int complexity = 0;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(SwitchStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();
        int nestingPenalty = Math.max(0, calculateNestingDepth(method) - 1);
        return complexity + nestingPenalty;
    }

    // Conta il numero di parametri del metodo
    public int calculateParameterCount(MethodDeclaration method) {
        return method.getParameters().size();
    }

    //  Misura la profondità massima delle strutture di controllo annidate
    public int calculateNestingDepth(MethodDeclaration method) {
        return calculateNestingDepthRecursive(method, 0);
    }

    /*
    - Aumenta il livello per ogni costrutto: if, for, while, switch, try, catch, ecc.
    - Esplora tutti i sotto-nodi ricorsivamente
     */
    private int calculateNestingDepthRecursive(Node node, int currentDepth) {
        int maxDepth = currentDepth;

        if (node instanceof IfStmt ||
                node instanceof ForStmt ||
                node instanceof ForEachStmt ||
                node instanceof WhileStmt ||
                node instanceof DoStmt ||
                node instanceof SwitchStmt ||
                node instanceof TryStmt ||
                node instanceof CatchClause) {
            currentDepth++;
        }

        for (Node child : node.getChildNodes()) {
            int childDepth = calculateNestingDepthRecursive(child, currentDepth);
            if (childDepth > maxDepth) {
                maxDepth = childDepth;
            }
        }

        return maxDepth;
    }

    // Conta il numero diretto di statement
    public int calculateStatementCount(MethodDeclaration method) {
        return method.getBody().map(b -> b.getStatements().size()).orElse(0);
    }

    // Calcola la complessità del tipo restituito
    public int calculateReturnTypeComplexity(MethodDeclaration method) {
        Type returnType = method.getType();
        return computeTypeComplexity(returnType);
    }

    /*
    1 per tipi primitivi o nominali semplici
    +1 per ogni array o parametro generico annidato
     */
    private int computeTypeComplexity(Type type) {
        if (type.isPrimitiveType()) return 1;
        if (type.isArrayType()) return 1 + computeTypeComplexity(type.asArrayType().getComponentType());

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            int complexity = 1; // tipo base

            if (cit.getTypeArguments().isPresent()) {
                for (Type arg : cit.getTypeArguments().get()) {
                    complexity += computeTypeComplexity(arg); // ricorsione su ciascun parametro
                }
            }

            return complexity;
        }

        // fallback per altri tipi
        return 1;
    }

    //  Conta le variabili locali dichiarate nel metodo, escludendo i parametri
    public int calculateLocalVariableCount(MethodDeclaration method) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getParentNode().isPresent() &&
                        !(v.getParentNode().get() instanceof com.github.javaparser.ast.body.Parameter))
                .toList()
                .size();
    }
}
