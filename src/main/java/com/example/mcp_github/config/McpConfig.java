package com.example.mcp_github.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.mcp_github.tools.actions.GitHubActionsTools;
import com.example.mcp_github.tools.branch.GitHubBranchTools;
import com.example.mcp_github.tools.commit.GitHubCommitTools;
import com.example.mcp_github.tools.file.GitHubFileTools;
import com.example.mcp_github.tools.issue.GitHubIssueTools;
import com.example.mcp_github.tools.project.ProjectContextTools;
import com.example.mcp_github.tools.pullrequest.GitHubPullRequestTools;
import com.example.mcp_github.tools.release.GitHubReleaseTools;
import com.example.mcp_github.tools.repository.GitHubRepositoryTools;
import com.example.mcp_github.tools.social.GitHubSocialTools;
import com.example.mcp_github.tools.structure.ProjectStructureTool;
import com.example.mcp_github.tools.structure.ReviewStructureTool;
import com.example.mcp_github.tools.user.GitHubUserTools;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider mcpTools(
            GitHubActionsTools actionsTools,
            GitHubBranchTools branchTools,
            GitHubCommitTools commitTools,
            GitHubFileTools fileTools,
            GitHubIssueTools issueTools,
            ProjectContextTools projectTools,
            GitHubPullRequestTools pullRequestTools,
            GitHubReleaseTools releaseTools,
            GitHubRepositoryTools repositoryTools,
            GitHubSocialTools socialTools,
            ProjectStructureTool structureTool,
            ReviewStructureTool reviewStructureTool,
            GitHubUserTools userTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        actionsTools,
                        branchTools,
                        commitTools,
                        fileTools,
                        issueTools,
                        projectTools,
                        pullRequestTools,
                        releaseTools,
                        repositoryTools,
                        socialTools,
                        structureTool,
                        reviewStructureTool,
                        userTools)
                .build();
    }
}
