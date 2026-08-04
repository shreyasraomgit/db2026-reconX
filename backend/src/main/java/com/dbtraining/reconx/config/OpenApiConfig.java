package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ============================================================================
 * OpenApiConfig — TICKET-ADV058
 * ============================================================================
 * WHAT:    Customises the OpenAPI document Springdoc generates (title, version,
 *          description, contact + bearerAuth security scheme).
 * HOW:     Single @Bean of type io.swagger.v3.oas.models.OpenAPI.
 * WHY:     Swagger UI on /api/swagger-ui.html becomes the single source of
 *          truth for the API contract — front-end and QA teams read it
 *          instead of digging through controllers.
 * OBSERVE: After wiring, the title in the top-left of Swagger UI is
 *          "ReconX API" and a green "Authorize" button accepts bearer JWTs.
 * ============================================================================
 *
 *  TODO(TICKET-ADV058):
 *    @Bean
 *    public OpenAPI reconxOpenAPI() {
 *        return new OpenAPI()
 *            .info(new Info()
 *                .title("ReconX API")
 *                .version("v1")
 *                .description("Enterprise Trade Reconciliation Platform (Advanced Track)")
 *                .contact(new Contact().name("DB TDI Training").email("tdi@db.com")))
 *            .components(new Components().addSecuritySchemes("bearerAuth",
 *                new SecurityScheme()
 *                    .type(SecurityScheme.Type.HTTP)
 *                    .scheme("bearer")
 *                    .bearerFormat("JWT")))
 *            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
 *    }
 *
 *  HINT: Without this bean Springdoc still produces a default OpenAPI doc —
 *        you'll see Swagger UI work, but with generic metadata and no
 *        "Authorize" button.
 * ============================================================================
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReconX API")
                        .version("v1")
                        .description("""
                                Enterprise Trade Reconciliation Platform (Advanced Track).

                                **Getting started:** open the `auth` section below, run \
                                POST /auth/login with a seeded user (e.g. trader@db.com / \
                                trader123), copy the `token` from the response, then click \
                                the green **Authorize** button at the top of this page and \
                                paste it in. Every other endpoint needs that.""")
                        .contact(new Contact().name("DB TDI Training").email("tdi@db.com")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                // Supplies the per-tag descriptions shown under each section heading
                // in Swagger UI. Does NOT control section order — springdoc's
                // auto-discovered GroupedOpenApi groups render tags alphabetically
                // regardless of this list's order (see tagsSorter in application.yml).
                .tags(List.of(
                        new Tag().name("auth").description("Step 1 — log in to get a JWT"),
                        new Tag().name("trades").description("Step 2 — create/list/update trades"),
                        new Tag().name("recon").description("Reconciliation runs and break resolution"),
                        new Tag().name("audit").description("Read-only change history")));
    }

    // "all" is the default selected group (see springdoc.swagger-ui.urls-primary-name
    // in application.yml) — every controller (auth, trades, recon, audit) in one
    // place. "public" and "admin" stay as narrower views for teams that only care
    // about one slice of the API.
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("all")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/v1/trades/**", "/v1/recon/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/v1/admin/**", "/auth/**", "/v1/audit/**", "/actuator/**")
                .build();
    }
}
