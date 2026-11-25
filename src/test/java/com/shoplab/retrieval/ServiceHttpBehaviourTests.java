package com.shoplab.retrieval;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.shoplab.retrieval.support.PostgresTest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** What changes when the search is reached over the network instead of in process (specs/LAB-129, research D-7). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"shoplab.retrieval.embedding.auto-index=false",
                "management.endpoint.health.show-components=always"})
@PostgresTest
class ServiceHttpBehaviourTests {

    private static final String EMBEDDINGS = "/v1/embeddings";

    @RegisterExtension
    static WireMockExtension gateway = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().gzipDisabled(true)).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void gatewayUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> gateway.baseUrl() + "/v1");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
    }

    private URI search(String query) {
        return URI.create("http://localhost:" + port + "/api/knowledge/search?limit=3&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    private static void stubEmbeddings() {
        String vector = String.join(",", Collections.nCopies(1024, "0.1"));
        gateway.stubFor(post(urlPathEqualTo(EMBEDDINGS)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"object\":\"list\",\"model\":\"embeddings\",\"data\":[{\"object\":\"embedding\","
                        + "\"index\":0,\"embedding\":[" + vector + "]}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
    }

    static String[] queriesTheChatCanCompose() {
        // The longest message of the chat, in characters of three bytes, and the signs a URL could mangle.
        return new String[] {"€".repeat(4000), "¿{x} & a+b #1 100% {} ? devolución"};
    }

    @ParameterizedTest
    @MethodSource("queriesTheChatCanCompose")
    void theWholeQueryReachesTheModel(String query) {
        stubEmbeddings();

        client().get().uri(search(query)).exchange().expectStatus().isOk();

        assertThat(gateway.getAllServeEvents().getFirst().getRequest().getBodyAsString()).contains(query);
    }

    @Test
    void theIdOfTheRequestReachesTheModelGateway() {
        stubEmbeddings();

        client().get().uri(search("envios")).header("X-Request-Id", "lab129-abc").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "lab129-abc");

        gateway.verify(postRequestedFor(urlPathEqualTo(EMBEDDINGS))
                .withHeader("x-shoplab-request-id", equalTo("lab129-abc")));
    }

    @Test
    void readinessDependsOnTheDatabase() {
        client().get().uri("/actuator/health/readiness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.components.db.status").isEqualTo("UP");
    }
}
