package com.example.mcp_github.model;

import java.time.LocalDate;
import java.util.List;

public record ContributorStats(
        String name,
        String email,
        int commitCount,
        LocalDate firstCommit,
        LocalDate lastCommit,
        List<String> filesPaths
) {}
