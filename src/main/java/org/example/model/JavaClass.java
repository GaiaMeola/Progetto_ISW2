package org.example.model;

import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.utilities.JavaParserUtil;

import java.util.*;

public class JavaClass {
    private static final String MAIN_METHOD_SIGNATURE = " public static void main(String[] args)";

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

    public JavaClass(String name, String classBody, Release release, boolean update) {
        this.name = name;
        this.classBody = classBody;
        this.methods = new HashMap<>();
        this.methodsMetrics = new HashMap<>();
        this.release = release;
        this.updateMethodsMap(update);
        this.metrics = new Metrics();
        this.classCommits = new ArrayList<>();
        this.lOCAddedByClass = new ArrayList<>();
        this.lOCRemovedByClass = new ArrayList<>();
    }

    private void updateMethodsMap(boolean update) {
        CompilationUnit cu = StaticJavaParser.parse(this.classBody);
        cu.getPackageDeclaration().ifPresent(packageDeclaration ->
                this.packageName = packageDeclaration.getNameAsString());
        cu.getTypes().stream()
                .findFirst()
                .map(TypeDeclaration::getNameAsString)
                .ifPresent(className -> this.simpleName = className);

        cu.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
            String signature = JavaParserUtil.getSignature(methodDeclaration);
            methods.put(signature, JavaParserUtil.getStringBody(methodDeclaration));
            if (update) {
                MethodMetrics methodMetrics = new MethodMetrics();
                methodsMetrics.put(signature, methodMetrics);
                methodMetrics.setParameterCount(JavaParserUtil.computeParameterCount(methodDeclaration));
                methodMetrics.setLinesOfCode(JavaParserUtil.computeEffectiveLOC(methodDeclaration));
                methodMetrics.setStatementCount(JavaParserUtil.computeStatementCount(methodDeclaration));
                methodMetrics.setCyclomaticComplexity(JavaParserUtil.computeCyclomaticComplexity(methodDeclaration));
                methodMetrics.setNestingDepth(JavaParserUtil.computeNestingDepth(methodDeclaration));
                methodMetrics.setMethodAccessor(methodDeclaration.getAccessSpecifier().asString());
                methodDeclaration.getBody().ifPresent(body ->
                        methodMetrics.setCognitiveComplexity(JavaParserUtil.calculateCognitiveComplexity(body)));
                methodMetrics.setBeginLine(methodDeclaration.getBegin().orElse(new Position(0, 0)).line);
                methodMetrics.setEndLine(methodDeclaration.getEnd().orElse(new Position(0, 0)).line);
                methodMetrics.setSimpleName(methodDeclaration.getNameAsString());
                methodMetrics.setAge(this.release.getId());
            }
        });

        methodsMetrics.keySet().removeIf(key -> key.contains(MAIN_METHOD_SIGNATURE));
        methods.keySet().removeIf(key -> key.contains(MAIN_METHOD_SIGNATURE));
    }

    public void addCommitToClass(Commit commit) {
        this.classCommits.add(commit);
    }

    public void addLOCAddedByClass(Integer lOCAddedByEntry) {
        lOCAddedByClass.add(lOCAddedByEntry);
    }

    public void addLOCRemovedByClass(Integer lOCRemovedByEntry) {
        lOCRemovedByClass.add(lOCRemovedByEntry);
    }

    @Override
    public String toString() {
        return "JavaClass{" +
                "name='" + name + '\'' +
                ", contentOfClass='" + classBody + '\'' +
                ", release=" + release +
                ", metrics=" + metrics +
                ", commitsThatTouchTheClass=" + classCommits +
                ", lOCAddedByClass=" + lOCAddedByClass +
                ", lOCRemovedByClass=" + lOCRemovedByClass +
                '}';
    }

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
}
