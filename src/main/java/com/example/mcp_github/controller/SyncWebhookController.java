package com.example.mcp_github.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.mcp_github.tools.structure.ProjectStructureTool;

@RestController
@RequestMapping("/webhook")
public class SyncWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SyncWebhookController.class);

    private final ProjectStructureTool projectStructureTool;

    public SyncWebhookController(ProjectStructureTool projectStructureTool) {
        this.projectStructureTool = projectStructureTool;
    }

    @PostMapping("/push")
    public String handlePushWebhook(@RequestBody(required = false) String payload) {
        log.info("Webhook received, triggering documentation sync for rollo_docs features...");
        // For the PoC, we trigger a global rebuild of the feature documentation based on updated files.
        try {
            // Using default project in memory 
            projectStructureTool.analyzeProjectStructure(null, null, null, false);
            return "Documentation synced successfully for features.";
        } catch (Exception e) {
            log.error("Failed to sync documentation on webhook", e);
            return "Documentation sync failed: " + e.getMessage();
        }
    }
}
