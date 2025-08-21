package org.example.model;

import com.github.javaparser.Position;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.utilities.JavaParserUtil;

import java.util.*;

public class JavaClass {

    private final String name;
    private final String classBody;
    private String packageName = "";
    private String simpleName = "";

    private final Map<String, String> methods;
    private final Map<String, MethodMetrics> methodsMetrics;
    private final Release release;
    private final Metrics metrics;
    private final List<Commit> classCommits;
    private final List<Integer> lOCAddedByClass;
    private final List<Integer> lOCRemovedByClass;

    private static final String MAIN_METHOD_SIGNATURE = "main";

    public JavaClass(String name, String classBody, Release release, boolean update) {
        this.name = name;
        this.classBody = classBody;
        this.release = release;
        this.methods = new HashMap<>();
        this.methodsMetrics = new HashMap<>();
        this.metrics = new Metrics();
        this.classCommits = new ArrayList<>();
        this.lOCAddedByClass = new ArrayList<>();
        this.lOCRemovedByClass = new ArrayList<>();
        parseAndUpdateMethods(update);
    }

    private void parseAndUpdateMethods(boolean update) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(this.classBody);
        } catch (ParseProblemException e) {
            System.err.println("Errore nel parsing della classe " + name + ": " + e.getMessage());
            return;
        }

        // Package e nome semplice della classe
        cu.getPackageDeclaration().ifPresent(pkg -> this.packageName = pkg.getNameAsString());
        cu.getTypes().stream().findFirst()
                .map(TypeDeclaration::getNameAsString)
                .ifPresent(name -> this.simpleName = name);

        // Parsing dei metodi
        cu.findAll(MethodDeclaration.class).forEach(md -> processMethod(md, update));
    }

    private void processMethod(MethodDeclaration methodDeclaration, boolean update) {
        String signature = JavaParserUtil.getSignature(methodDeclaration);
        methods.put(signature, JavaParserUtil.getStringBody(methodDeclaration));

        // Salta il main
        if (MAIN_METHOD_SIGNATURE.equals(methodDeclaration.getNameAsString())) return;

        if (!update) return; // se update=false, non calcolare metriche

        // Calcola metriche
        MethodMetrics methodMetrics = new MethodMetrics();
        methodsMetrics.put(signature, methodMetrics);

        methodMetrics.setParameterCount(JavaParserUtil.computeParameterCount(methodDeclaration));
        methodMetrics.setLinesOfCode(JavaParserUtil.computeEffectiveLOC(methodDeclaration));
        methodMetrics.setStatementCount(JavaParserUtil.computeStatementCount(methodDeclaration));
        methodMetrics.setCyclomaticComplexity(JavaParserUtil.computeCyclomaticComplexity(methodDeclaration));
        methodMetrics.setNestingDepth(JavaParserUtil.computeNestingDepth(methodDeclaration));
        methodMetrics.setMethodAccessor(methodDeclaration.getAccessSpecifier().asString());
        methodDeclaration.getBody().ifPresent(body ->
                methodMetrics.setCognitiveComplexity(JavaParserUtil.calculateCognitiveComplexity(body))
        );
        methodMetrics.setBeginLine(methodDeclaration.getBegin().orElse(new Position(0, 0)).line);
        methodMetrics.setEndLine(methodDeclaration.getEnd().orElse(new Position(0, 0)).line);
        methodMetrics.setSimpleName(methodDeclaration.getNameAsString());
        methodMetrics.setAge(this.release.getId());
    }

    // Commit e LOC
    public void addCommitToClass(Commit commit) {
        classCommits.add(commit);
    }

    public void addLOCAddedByClass(Integer locAdded) {
        lOCAddedByClass.add(locAdded);
    }

    public void addLOCRemovedByClass(Integer locRemoved) {
        lOCRemovedByClass.add(locRemoved);
    }

    // Getters e setter
    public String getClassName() {
        return this.packageName + '.' + this.simpleName;
    }

    public String getName() {
        return name;
    }

    public String getClassBody() {
        return classBody;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public Map<String, String> getMethods() {
        return methods;
    }

    public Map<String, MethodMetrics> getMethodsMetrics() {
        return methodsMetrics;
    }

    public Release getRelease() {
        return release;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public List<Commit> getClassCommits() {
        return classCommits;
    }

    public List<Integer> getLOCAddedByClass() {
        return lOCAddedByClass;
    }

    public List<Integer> getLOCRemovedByClass() {
        return lOCRemovedByClass;
    }

    /**
     * Verifica se la classe ha almeno un metodo parsato correttamente.
     * @return true se la mappa dei metodi non è vuota, false altrimenti
     */
    public boolean isHasMap() {
        return methods != null && !methods.isEmpty();
    }

    @Override
    public String toString() {
        return "JavaClass{" +
                "name='" + name + '\'' +
                ", classBody='" + classBody + '\'' +
                ", release=" + release +
                ", metrics=" + metrics +
                ", commits=" + classCommits +
                ", locAdded=" + lOCAddedByClass +
                ", locRemoved=" + lOCRemovedByClass +
                '}';
    }
}