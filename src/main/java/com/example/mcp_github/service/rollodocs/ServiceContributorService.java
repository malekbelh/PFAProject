package com.example.mcp_github.service.rollodocs;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ContributorStats;
import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.GitHubCommit;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.GitHubService;

@Service
public class ServiceContributorService {

    private final GitHubService gitHubService;

    public ServiceContributorService(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    public List<ContributorStats> fetchContributions(DetectedService service, RepositorySnapshot snapshot) {
        List<String> ecosystemPaths = resolveEcosystemPaths(service, snapshot);
        Map<String, ContributorStatsBuilder> builders = new HashMap<>();
        Set<String> processedCommits = new HashSet<>();
        List<GitHubCommit> allCommitsForEcosystem = new ArrayList<>();

        for (String path : ecosystemPaths) {
            List<GitHubCommit> commits = gitHubService.getRepositoryCommitsByPath(snapshot.owner(), snapshot.repo(), path, 100);
            if (commits == null) continue;

            for (GitHubCommit commit : commits) {
                if (commit.sha() != null && !processedCommits.contains(commit.sha())) {
                    processedCommits.add(commit.sha());
                    allCommitsForEcosystem.add(commit);
                }
            }
        }

        // Aggregate by author
        for (GitHubCommit commit : allCommitsForEcosystem) {
            String authorName = commit.commit().author().name();
            String authorEmail = commit.commit().author().email();
            String dateStr = commit.commit().author().date(); // ISO date string
            LocalDate date = null;
            try {
                if (dateStr != null && dateStr.length() >= 10) {
                    date = LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                }
            } catch (Exception ignored) {}

            String key = authorEmail != null ? authorEmail : authorName;
            ContributorStatsBuilder builder = builders.computeIfAbsent(key, k -> new ContributorStatsBuilder(authorName, authorEmail));
            builder.addCommit(date);
            // We could add file paths here but we need to know which files this commit touched.
            // For simplicity in this aggregated context, we don't map individual paths per commit
            // without fetching the full commit details, which would be too many HTTP calls.
        }

        List<ContributorStats> stats = new ArrayList<>();
        for (ContributorStatsBuilder b : builders.values()) {
            stats.add(b.build());
        }

        // Sort by commit count descending
        stats.sort((a, b) -> Integer.compare(b.commitCount(), a.commitCount()));

        return stats;
    }

    public List<GitHubCommit> fetchCommits(DetectedService service, RepositorySnapshot snapshot) {
        List<String> ecosystemPaths = resolveEcosystemPaths(service, snapshot);
        Set<String> processedCommits = new HashSet<>();
        List<GitHubCommit> allCommitsForEcosystem = new ArrayList<>();

        for (String path : ecosystemPaths) {
            List<GitHubCommit> commits = gitHubService.getRepositoryCommitsByPath(snapshot.owner(), snapshot.repo(), path, 100);
            if (commits == null) continue;

            for (GitHubCommit commit : commits) {
                if (commit.sha() != null && !processedCommits.contains(commit.sha())) {
                    processedCommits.add(commit.sha());
                    allCommitsForEcosystem.add(commit);
                }
            }
        }
        
        allCommitsForEcosystem.sort((a, b) -> {
            String dateA = a.commit().author().date();
            String dateB = b.commit().author().date();
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA); // Reverse chronological
        });

        return allCommitsForEcosystem;
    }

    private List<String> resolveEcosystemPaths(DetectedService service, RepositorySnapshot snapshot) {
        Set<String> classNames = new HashSet<>();
        classNames.add(service.className());
        if (service.ecosystem() != null) {
            classNames.addAll(service.ecosystem().implementsInterfaces());
            classNames.addAll(service.ecosystem().callingControllers());
            classNames.addAll(service.ecosystem().usedRepositories());
            classNames.addAll(service.ecosystem().usedDtos());
            classNames.addAll(service.ecosystem().manipulatedEntities());
        }

        List<String> paths = new ArrayList<>();
        for (String path : snapshot.allPaths()) {
            String fileName = path.substring(path.lastIndexOf('/') + 1).replace(".java", "").replace(".ts", "").replace(".kt", "");
            if (classNames.contains(fileName)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static class ContributorStatsBuilder {
        String name;
        String email;
        int commitCount = 0;
        LocalDate firstCommit = null;
        LocalDate lastCommit = null;

        ContributorStatsBuilder(String name, String email) {
            this.name = name;
            this.email = email;
        }

        void addCommit(LocalDate date) {
            commitCount++;
            if (date != null) {
                if (firstCommit == null || date.isBefore(firstCommit)) {
                    firstCommit = date;
                }
                if (lastCommit == null || date.isAfter(lastCommit)) {
                    lastCommit = date;
                }
            }
        }

        ContributorStats build() {
            return new ContributorStats(name, email, commitCount, firstCommit, lastCommit, new ArrayList<>());
        }
    }
}
