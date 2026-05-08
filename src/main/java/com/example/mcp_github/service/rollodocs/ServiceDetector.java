package com.example.mcp_github.service.rollodocs;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.DetectionReason;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

@Service
public class ServiceDetector {

    public List<DetectedService> detect(RepositorySnapshot snapshot) {
        List<DetectedService> services = new ArrayList<>();

        for (String path : snapshot.allPaths()) {
    String lowerPath = path.toLowerCase();
    // Detect services based on directory name (case‑insensitive)
    if (lowerPath.contains("/service/") || lowerPath.contains("/services/")) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String packageName = extractPackageName(path);
        services.add(new DetectedService(className, path, packageName, DetectionReason.FOLDER_SERVICE, null));
        continue;
    }
    if (!path.endsWith(".java") && !path.endsWith(".ts") && !path.endsWith(".js") && !path.endsWith(".kt")) {
        continue;
    }

            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String packageName = extractPackageName(path);
            

            if (className.endsWith("Service")) {
                services.add(new DetectedService(className, path, packageName, DetectionReason.SUFFIX_SERVICE, null));
                continue;
            }

            if (className.startsWith("Service")) {
                services.add(new DetectedService(className, path, packageName, DetectionReason.PREFIX_SERVICE, null));
                continue;
            }

            // Check content for annotations if it's in keyFiles
            if (snapshot.keyFiles().containsKey(path)) {
                String content = snapshot.keyFiles().get(path);
                if (content.contains("@Service") || content.contains("@Injectable") || (content.contains("@Component") && content.toLowerCase().contains("service"))) {
                    services.add(new DetectedService(className, path, packageName, DetectionReason.ANNOTATION_SERVICE, null));
                    continue;
                }
            }
        }

        return services;
    }

    private String extractPackageName(String path) {
        if (path.contains("src/main/java/")) {
            String p = path.substring(path.indexOf("src/main/java/") + 14, Math.max(path.indexOf("src/main/java/") + 14, path.lastIndexOf('/')));
            return p.replace('/', '.');
        } else if (path.contains("src/")) {
            String p = path.substring(path.indexOf("src/") + 4, Math.max(path.indexOf("src/") + 4, path.lastIndexOf('/')));
            return p.replace('/', '.');
        }
        return "";
    }
}
