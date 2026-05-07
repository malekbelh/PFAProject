package com.example.mcp_github.model;

import java.util.Map;

/**
 * Stores example "shots" for zero, one, and few-shot architect prompting.
 */
public class ReferenceArchitectures {

    /**
     * One-shot example for a Spring Boot MVC project.
     */
    public static final String SPRING_BOOT_MVC_SHOT = """
            [EXAMPLE 1: Java/Spring Boot MVC]
            Structure:
            - src/main/java/com/example/controller/
            - src/main/java/com/example/service/
            - src/main/java/com/example/repository/
            - src/main/java/com/example/model/
            - src/main/resources/application.yml
            
            Analysis:
            This is a classic MVC pattern. The controller layer handles external requests, delegating 
            to a service layer that contains the business logic. Data persistence is managed via 
            Spring Data JPA repositories. This structure provides clear segregation of duties 
            and high maintainability for enterprise CRUD applications.
            """;

    /**
     * One-shot example for a Node.js Express project.
     */
    public static final String NODE_EXPRESS_SHOT = """
            [EXAMPLE 2: Node.js/Express]
            Structure:
            - src/routes/
            - src/controllers/
            - src/middleware/
            - src/models/
            - package.json
            
            Analysis:
            This is an Express-style layered architecture. Routes are explicitly mapped to 
            controllers, keeping the logic clean. Middlewares handle cross-cutting concerns 
            like auth and error handling. This modular approach is ideal for lightweight rest 
            APIs and rapid microservice development.
            """;

    /**
     * Few-shot context for pattern recognition.
     */
    public static final Map<String, String> PATTERN_SHOTS = Map.of(
            "CLEAN", """
                    [SHOT: Clean Architecture]
                    Signals: Use cases (application layer), Domain entities, External adapters (infra).
                    Verdict: High focus on testability and domain isolation.
                    """,
            "HEXAGONAL", """
                    [SHOT: Hexagonal/Ports & Adapters]
                    Signals: Ports interfaces, Adapters (input/output), Infrastructure mapping.
                    Verdict: Extremely flexible, allowing multiple external systems to interact 
                    with the same business core.
                    """
    );
}
