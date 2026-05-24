package com.example.mcp_github.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ContributorStats;
import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.DocFragment;
import com.example.mcp_github.model.DocSection;
import com.example.mcp_github.model.GitHubCommit;
import com.example.mcp_github.model.ProjectFingerprint;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.ProjectStructureAnalyzer.AnalysisResult;
import com.example.mcp_github.service.TreeSitterAnalyzer;
import com.example.mcp_github.service.TreeSitterAnalyzer.AstFileSummary;
import com.example.mcp_github.service.rollodocs.GitHubResponseCache;
import com.example.mcp_github.service.rollodocs.ServiceContributorService;
import com.example.mcp_github.service.rollodocs.ServiceDetector;
import com.example.mcp_github.service.rollodocs.ServiceEcosystemResolver;
import com.example.mcp_github.service.rollodocs.generators.RolloDocsIndexGenerator;
import com.example.mcp_github.service.rollodocs.generators.SectionGenerators;

@Service
public class RolloDocsBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RolloDocsBuilder.class);

    private final ServiceDetector detector;
    private final ServiceEcosystemResolver ecosystemResolver;
    private final ServiceContributorService contributorService;
    private final RolloDocsIndexGenerator indexGenerator;
    private final DocumentationWriterService writerService;
    private final GitHubResponseCache cache;
    private final DocumentationContextBuilder contextBuilder;
    private final TreeSitterAnalyzer treeSitterAnalyzer;
    private final LocalFileTreeService localFileTreeService;

    public RolloDocsBuilder(
            ServiceDetector detector,
            ServiceEcosystemResolver ecosystemResolver,
            ServiceContributorService contributorService,
            RolloDocsIndexGenerator indexGenerator,
            DocumentationWriterService writerService,
            GitHubResponseCache cache,
            DocumentationContextBuilder contextBuilder,
            TreeSitterAnalyzer treeSitterAnalyzer,
            LocalFileTreeService localFileTreeService) {
        this.detector = detector;
        this.ecosystemResolver = ecosystemResolver;
        this.contributorService = contributorService;
        this.indexGenerator = indexGenerator;
        this.writerService = writerService;
        this.cache = cache;
        this.contextBuilder = contextBuilder;
        this.treeSitterAnalyzer = treeSitterAnalyzer;
        this.localFileTreeService = localFileTreeService;
    }

    public ProjectFingerprint build(String projectPath, String owner, String repo, String branch, RepositorySnapshot snapshot, AnalysisResult analysis) {
        try {
            cache.clear();
            long start = System.currentTimeMillis();

            // ── 1. Choix de la source : local (FileSystem) ou GitHub API ──────
            //
            // Si le projet est accessible sur le disque local → on lit depuis
            // le filesystem (FileSystemTools) : pas de limite de fichiers,
            // pas de token requis, fonctionne hors-ligne.
            //
            // Sinon → on utilise le snapshot GitHub déjà récupéré.
            //
            RepositorySnapshot effectiveSnapshot;
            if (localFileTreeService.isLocalPathAvailable(projectPath)) {
                logger.info("📁 Source : filesystem local ({})", projectPath);
                effectiveSnapshot = localFileTreeService.snapshotFromLocal(
                        projectPath, owner, repo, branch);
            } else {
                logger.info("☁️  Source : API GitHub ({}/{})", owner, repo);
                effectiveSnapshot = snapshot;
            }

            // ── 2. Analyse AST globale (TreeSitterAnalyzer / JavaParser) ──────
            Map<String, AstFileSummary> astSummaries =
                    treeSitterAnalyzer.analyzeFiles(effectiveSnapshot.keyFiles());

            logger.info("🌳 AST analysé : {} fichiers", astSummaries.size());

            List<DetectedService> services = detector.detect(effectiveSnapshot);

            for (int i = 0; i < services.size(); i++) {
                DetectedService service = services.get(i);
                try {
                    DetectedService resolved = new DetectedService(
                            service.className(),
                            service.path(),
                            service.packageName(),
                            service.reason(),
                            ecosystemResolver.resolve(service, effectiveSnapshot)
                    );
                    services.set(i, resolved);

                    List<ContributorStats> contributors = contributorService.fetchContributions(resolved, effectiveSnapshot);
                    List<GitHubCommit> commits = contributorService.fetchCommits(resolved, effectiveSnapshot);

                    // ── 3. Résumé AST pour ce service ────────────────────────
                    AstFileSummary astSummary = astSummaries.get(resolved.path());
                    if (astSummary == null) {
                        astSummary = astSummaries.entrySet().stream()
                                .filter(e -> e.getKey().contains(resolved.className()))
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(null);
                    }

                    // ── 4. Génération des sections avec données AST ───────────
                    Map<DocSection, List<DocFragment>> sections = new HashMap<>();
                    sections.put(DocSection.DESCRIPTION,
                            SectionGenerators.generateDescription(resolved, astSummary));
                    sections.put(DocSection.CLASS_DIAGRAM,
                            SectionGenerators.generateClassDiagram(resolved, astSummary));
                    sections.put(DocSection.SEQUENCE_DIAGRAM,
                            SectionGenerators.generateSequenceDiagram(resolved, astSummary));
                    sections.put(DocSection.COMPONENTS,
                            SectionGenerators.generateComponentsList(resolved));
                    sections.put(DocSection.DEPENDENCIES,
                            SectionGenerators.generateDependencies(resolved));
                    sections.put(DocSection.PUBLIC_API,
                            SectionGenerators.generatePublicApi(resolved, astSummary));
                    sections.put(DocSection.COMMITS,
                            SectionGenerators.generateCommits(resolved, commits));
                    sections.put(DocSection.CONTRIBUTORS,
                            SectionGenerators.generateContributors(resolved, contributors));
                    sections.put(DocSection.META,
                            SectionGenerators.generateMeta(resolved, effectiveSnapshot, start, cache.getMisses()));
                    sections.put(DocSection.README,
                            SectionGenerators.generateReadme(resolved, sections));

                    writerService.writeServiceDoc(projectPath, resolved.className(), sections);

                } catch (Exception e) {
                    logger.error("Error generating documentation for service {}: {}",
                            service.className(), e.getMessage());
                }
            }

            List<DocFragment> indexFragments = indexGenerator.generate(services, effectiveSnapshot);
            writerService.writeIndexDoc(projectPath, indexFragments);

            return contextBuilder.buildFingerprint(analysis, List.of());

        } catch (Exception e) {
            logger.error("Error in RolloDocsBuilder", e);
            return null;
        }
    }
}
