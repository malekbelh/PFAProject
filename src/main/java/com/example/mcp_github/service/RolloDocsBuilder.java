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

    public RolloDocsBuilder(
            ServiceDetector detector,
            ServiceEcosystemResolver ecosystemResolver,
            ServiceContributorService contributorService,
            RolloDocsIndexGenerator indexGenerator,
            DocumentationWriterService writerService,
            GitHubResponseCache cache,
            DocumentationContextBuilder contextBuilder) {
        this.detector = detector;
        this.ecosystemResolver = ecosystemResolver;
        this.contributorService = contributorService;
        this.indexGenerator = indexGenerator;
        this.writerService = writerService;
        this.cache = cache;
        this.contextBuilder = contextBuilder;
    }

    public ProjectFingerprint build(String projectPath, String owner, String repo, String branch, RepositorySnapshot snapshot, AnalysisResult analysis) {
        try {
            cache.clear();
            long start = System.currentTimeMillis();

            List<DetectedService> services = detector.detect(snapshot);
            
            for (int i = 0; i < services.size(); i++) {
                DetectedService service = services.get(i);
                try {
                    DetectedService resolved = new DetectedService(
                            service.className(),
                            service.path(),
                            service.packageName(),
                            service.reason(),
                            ecosystemResolver.resolve(service, snapshot)
                    );
                    services.set(i, resolved);

                    List<ContributorStats> contributors = contributorService.fetchContributions(resolved, snapshot);
                    List<GitHubCommit> commits = contributorService.fetchCommits(resolved, snapshot);

                    Map<DocSection, List<DocFragment>> sections = new HashMap<>();
                    sections.put(DocSection.DESCRIPTION, SectionGenerators.generateDescription(resolved));
                    sections.put(DocSection.CLASS_DIAGRAM, SectionGenerators.generateClassDiagram(resolved));
                    sections.put(DocSection.SEQUENCE_DIAGRAM, SectionGenerators.generateSequenceDiagram(resolved));
                    sections.put(DocSection.COMPONENTS, SectionGenerators.generateComponentsList(resolved));
                    sections.put(DocSection.DEPENDENCIES, SectionGenerators.generateDependencies(resolved));
                    sections.put(DocSection.PUBLIC_API, SectionGenerators.generatePublicApi(resolved));
                    sections.put(DocSection.COMMITS, SectionGenerators.generateCommits(resolved, commits));
                    sections.put(DocSection.CONTRIBUTORS, SectionGenerators.generateContributors(resolved, contributors));
                    sections.put(DocSection.META, SectionGenerators.generateMeta(resolved, snapshot, start, cache.getMisses()));
                    sections.put(DocSection.README, SectionGenerators.generateReadme(resolved, sections));

                    writerService.writeServiceDoc(projectPath, resolved.className(), sections);
                } catch (Exception e) {
                    logger.error("Error generating documentation for service {}: {}", service.className(), e.getMessage());
                }
            }

            List<DocFragment> indexFragments = indexGenerator.generate(services, snapshot);
            writerService.writeIndexDoc(projectPath, indexFragments);

            // We return the fingerprint based on the analysis (unchanged logic for fingerprint)
            return contextBuilder.buildFingerprint(analysis, List.of());
        } catch (Exception e) {
            logger.error("Error in RolloDocsBuilder", e);
            return null;
        }
    }
}
