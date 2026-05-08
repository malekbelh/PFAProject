package com.example.mcp_github.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.DocFragment;

@Service
public class DocFragmentRenderer {

    public String render(List<DocFragment> fragments) {
        StringBuilder sb = new StringBuilder();
        
        for (DocFragment fragment : fragments) {
            String content = fragment.content();
            
            switch (fragment.level()) {
                case INFERRED -> sb.append(content).append(" ⓘ *(").append(fragment.source()).append(")*\n\n");
                case PLACEHOLDER -> sb.append("> [!WARNING]\n> ").append(content).append("\n\n");
                case OBSERVED -> sb.append(content).append("\n\n");
            }
        }
        
        return sb.toString().trim();
    }
}
