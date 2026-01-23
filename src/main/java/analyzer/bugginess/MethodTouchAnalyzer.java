package analyzer.bugginess;

import analyzer.model.MethodInfo;
import analyzer.git.GitRepository;
import util.Configuration;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import java.util.*;

public class MethodTouchAnalyzer {

    private final GitRepository repo;

    public MethodTouchAnalyzer(GitRepository repo) {
        this.repo = repo;
    }

    // Controlla se un commit ha effettivamente modificato uno o più metodi
    public Set<MethodInfo> getTouchedMethods(RevCommit commit, String filePath, List<MethodInfo> candidateMethods) {
        Set<MethodInfo> touched = new HashSet<>();

        try {

            // Se è un root commit, lo salta
            if (commit.getParentCount() == 0) return touched;

            // Costruisce il diff tra commit e il suo parent
            RevCommit parent = repo.parseCommit(commit);
            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repo.getGit().getRepository());
                df.setDetectRenames(true);
                df.setDiffComparator(RawTextComparator.DEFAULT);

                List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

                // Per ogni file modificato nel commit
                for (DiffEntry diff : diffs) {
                    String changedFile = diff.getNewPath();
                    if (!changedFile.equals(filePath)) continue; // Se il file non è filePath, lo salta

                    // Ogni Edit rappresenta un blocco di righe aggiunte/modificate
                    List<Edit> edits = df.toFileHeader(diff).toEditList();

                    // Per ogni metodo nel file, controlla se una modifica (Edit) tocca le righe del metodo
                    for (MethodInfo method : candidateMethods) {
                        int start = method.getStartLine();
                        int end = method.getEndLine();

                        for (Edit edit : edits) {
                            boolean isTouched = (edit.getEndB() > start && edit.getBeginB() < end);
                            if (isTouched) {
                                touched.add(method);
                                break;
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            Configuration.logger.severe(String.format("Errore in getTouchedMethods() per commit %s", commit.getName()));
        }

        return touched;
    }
}
