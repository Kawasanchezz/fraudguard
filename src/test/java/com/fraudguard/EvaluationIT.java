package com.fraudguard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.cases.CaseService;
import com.fraudguard.evaluation.EvaluationResult;
import com.fraudguard.evaluation.FailSafePolicy;
import com.fraudguard.evaluation.Outcome;
import com.fraudguard.evaluation.RuleHit;
import com.fraudguard.profile.CustomerProfile;
import com.fraudguard.profile.CustomerProfileRepository;
import com.fraudguard.rules.RuleAdminController;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integração real: PostgreSQL + Redis em contêineres, HTTP de ponta a ponta. */
@SuppressWarnings("resource") // contêineres gerenciados pelo @Container
@AutoConfigureTestRestTemplate
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fraudguard.messaging.enabled=false",
                "fraudguard.clock-skew=P36500D",
                "management.server.port=0",
                "fraudguard.security.service-password=" + EvaluationIT.SERVICE_PW,
                "fraudguard.security.analyst-password=" + EvaluationIT.ANALYST_PW,
                "fraudguard.security.admin-password=" + EvaluationIT.ADMIN_PW})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvaluationIT {

    static final String SERVICE_PW = "it-service-password-0001";
    static final String ANALYST_PW = "it-analyst-password-0002";
    static final String ADMIN_PW = "it-admin-password-000003";

    @SuppressWarnings("resource") // ciclo de vida gerenciado pelo @Container
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Só a conexão inicial é folgada (rede do Docker Desktop); o timeout de comando segue 100 ms.
        r.add("spring.data.redis.connect-timeout", () -> "3s");
    }

    private static final Instant NOON_SP = Instant.parse("2026-01-15T15:00:00Z"); // 12h em São Paulo

    @Autowired TestRestTemplate http;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Autowired io.github.resilience4j.circuitbreaker.CircuitBreaker velocityCircuitBreaker;

    /** O proxy de portas do Docker Desktop pode recusar as primeiras conexões: espera o Redis responder. */
    @org.junit.jupiter.api.BeforeEach
    void redisReady() throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            try {
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
                velocityCircuitBreaker.reset();
                return;
            } catch (RuntimeException e) {
                Thread.sleep(500);
            }
        }
    }
    @Autowired CustomerProfileRepository profiles;

    private static final String CSRF = "teste-csrf-token-0123456789";

    /** Cabeçalho e cookie com o mesmo valor: é o que o painel web envia em POST/PUT em /cases e /admin. */
    private static HttpEntity<Object> comCsrf(Object corpo) {
        var cabecalhos = new org.springframework.http.HttpHeaders();
        cabecalhos.add(org.springframework.http.HttpHeaders.COOKIE, "XSRF-TOKEN=" + CSRF);
        cabecalhos.add("X-XSRF-TOKEN", CSRF);
        cabecalhos.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(corpo, cabecalhos);
    }

    private static String id() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    private Map<String, Object> tx(String txId, String customer, String amount, String device, String loc, String rcp) {
        return Map.of("transactionId", txId, "customerId", customer, "amount", new BigDecimal(amount),
                "channel", "PIX", "deviceId", device, "location", loc, "recipientId", rcp,
                "occurredAt", NOON_SP.toString());
    }

    private EvaluationResult evaluate(Map<String, Object> body) {
        var resp = http.withBasicAuth("service", SERVICE_PW).postForEntity("/fraud/evaluate", body, EvaluationResult.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @Test
    @Order(1)
    void normalTransactionIsApprovedAndReplayIsIdempotent() {
        String c = id(), t = id();
        profiles.save(new CustomerProfile(c, new BigDecimal("100"), null));

        var first = evaluate(tx(t, c, "120", "d1", "BR-SP", "r1"));
        assertThat(first.outcome()).isEqualTo(Outcome.APROVAR);
        assertThat(first.rulesetVersion()).isEqualTo(1);

        var replay = evaluate(tx(t, c, "999999", "d9", "XX", "r9")); // mesmo id: devolve a decisão original
        assertThat(replay).isEqualTo(first);
    }

    @Test
    @Order(2)
    void suspiciousTransactionIsBlockedWithExplanation() {
        String c = id();
        profiles.save(new CustomerProfile(c, new BigDecimal("100"), null));
        evaluate(tx(id(), c, "100", "d1", "BR-SP", "r1")); // linha de base de dispositivo/local/destinatário

        var r = evaluate(tx(id(), c, "100000", "d2", "BR-RJ", "r2"));
        assertThat(r.outcome()).isEqualTo(Outcome.BLOQUEAR);
        assertThat(r.reasons()).extracting(RuleHit::code).containsExactlyInAnyOrder(
                "HIGH_AMOUNT_VS_AVERAGE", "NEW_DEVICE_OR_LOCATION", "NEW_RECIPIENT_HIGH_AMOUNT");
        assertThat(r.score()).isEqualTo(95);
    }

    @Test
    @Order(3)
    void velocityWindowTriggersAfterTooManyTransactions() {
        String c = id();
        EvaluationResult last = null;
        for (int i = 0; i < 7; i++) {
            last = evaluate(tx(id(), c, "10", "d1", "BR-SP", "r1"));
        }
        assertThat(last.reasons()).extracting(RuleHit::code).contains("VELOCITY");
        assertThat(last.outcome()).isEqualTo(Outcome.REVISAR);
    }

    @Test
    @Order(4)
    void reviewCaseCanBeResolvedOnlyByAnalystAndOnlyOnce() {
        String c = id(), t = id();
        profiles.save(new CustomerProfile(c, new BigDecimal("100"), null));
        evaluate(tx(id(), c, "100", "d1", "BR-SP", "r1"));
        assertThat(evaluate(tx(t, c, "1000", "d1", "BR-SP", "r1")).outcome()).isEqualTo(Outcome.REVISAR);

        var type = new ParameterizedTypeReference<List<CaseService.CaseView>>() {};
        var open = http.withBasicAuth("analista", ANALYST_PW)
                .exchange("/cases?status=ABERTO", HttpMethod.GET, null, type).getBody();
        var mine = open.stream().filter(v -> v.transactionId().equals(t)).findFirst().orElseThrow();

        assertThat(http.withBasicAuth("service", SERVICE_PW)
                .exchange("/cases/" + mine.caseId() + "/approve", HttpMethod.POST, comCsrf(Map.of()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var approved = http.withBasicAuth("analista", ANALYST_PW)
                .exchange("/cases/" + mine.caseId() + "/approve", HttpMethod.POST,
                        comCsrf(Map.of("note", "cliente confirmou")), CaseService.CaseView.class);
        assertThat(approved.getBody().status().name()).isEqualTo("APROVADO");
        assertThat(approved.getBody().analyst()).isEqualTo("analista");

        assertThat(http.withBasicAuth("analista", ANALYST_PW)
                .exchange("/cases/" + mine.caseId() + "/block", HttpMethod.POST, comCsrf(Map.of()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(5)
    void ruleChangeTakesEffectWithoutDeployAndBumpsRulesetVersion() {
        String c = id();
        profiles.save(new CustomerProfile(c, new BigDecimal("100"), null));
        evaluate(tx(id(), c, "100", "d1", "BR-SP", "r1"));
        assertThat(evaluate(tx(id(), c, "1000", "d1", "BR-SP", "r1")).outcome()).isEqualTo(Outcome.REVISAR);

        // Desativa a regra de valor alto: sem deploy.
        var admin = http.withBasicAuth("admin", ADMIN_PW);
        var resp = admin.exchange("/admin/rules/HIGH_AMOUNT_VS_AVERAGE", HttpMethod.PUT,
                comCsrf(Map.of("enabled", false)), RuleAdminController.RuleView.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        int newVersion = resp.getBody().version();
        assertThat(newVersion).isGreaterThan(1);

        var after = evaluate(tx(id(), c, "1000", "d1", "BR-SP", "r1"));
        assertThat(after.outcome()).isEqualTo(Outcome.APROVAR);
        assertThat(after.rulesetVersion()).isEqualTo(newVersion);

        assertThat(http.withBasicAuth("analista", ANALYST_PW)
                .exchange("/admin/rules", HttpMethod.GET, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Restaura (novo registro de versão, histórico preservado).
        admin.exchange("/admin/rules/HIGH_AMOUNT_VS_AVERAGE", HttpMethod.PUT,
                comCsrf(Map.of("enabled", true, "weight", 40)), RuleAdminController.RuleView.class);
        var rules = admin.exchange("/admin/rules", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<RuleAdminController.RuleView>>() {}).getBody();
        assertThat(rules).hasSize(5);
        assertThat(admin.exchange("/admin/rules/HIGH_AMOUNT_VS_AVERAGE", HttpMethod.PUT,
                comCsrf(Map.of("weight", 500)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.exchange("/admin/rules/NOPE", HttpMethod.PUT,
                comCsrf(Map.of("weight", 5)), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    void invalidPayloadAndMissingCredentialsAreRejected() {
        assertThat(http.withBasicAuth("service", SERVICE_PW)
                .postForEntity("/fraud/evaluate", Map.of("transactionId", "x"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.postForEntity("/fraud/evaluate", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(7)
    void wrongPasswordAndUnknownUserAreRejected() {
        var body = Map.of("transactionId", "x");
        assertThat(http.withBasicAuth("service", "senha-errada-123456")
                .postForEntity("/fraud/evaluate", body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.withBasicAuth("service", "service")
                .postForEntity("/fraud/evaluate", body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.withBasicAuth("root", SERVICE_PW)
                .postForEntity("/fraud/evaluate", body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(8)
    void oversizedAndOutOfRangeInputsAreRefused() {
        var service = http.withBasicAuth("service", SERVICE_PW);
        var huge = Map.of("transactionId", "x".repeat(20_000), "customerId", "c", "amount", 1, "channel", "PIX");
        assertThat(service.postForEntity("/fraud/evaluate", huge, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

        assertThat(service.postForEntity("/fraud/evaluate",
                tx(id(), id(), "99999999999", "d", "l", "r"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(service.postForEntity("/fraud/evaluate",
                tx("x".repeat(65), id(), "10", "d", "l", "r"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(9)
    void ruleParamsAreValidatedBeforeBeingStored() {
        var admin = http.withBasicAuth("admin", ADMIN_PW);
        for (Map<String, Object> bad : List.of(
                Map.<String, Object>of("zone", "Nao/Existe"),
                Map.<String, Object>of("startHour", 99),
                Map.<String, Object>of("multiplier", -1),
                Map.<String, Object>of("nested", Map.of("a", 1)))) {
            assertThat(admin.exchange("/admin/rules/UNUSUAL_HOUR", HttpMethod.PUT,
                    comCsrf(Map.of("params", bad)), String.class).getStatusCode())
                    .as("params %s", bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @Order(11)
    void caseResolutionRequiresAValidBody() {
        var analyst = http.withBasicAuth("analista", ANALYST_PW);
        assertThat(analyst.exchange("/cases/1/approve", HttpMethod.POST, comCsrf(null), String.class)
                .getStatusCode().is4xxClientError()).isTrue();
        assertThat(analyst.exchange("/cases/1/approve", HttpMethod.POST, comCsrf(Map.of("note", "x".repeat(501))),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(analyst.exchange("/cases/1/approve", HttpMethod.POST, comCsrf(Map.of("note", "linha\nforjada")),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(12)
    void extremeOccurredAtIsRejectedInsteadOfTrippingTheCircuitBreaker() {
        var service = http.withBasicAuth("service", SERVICE_PW);
        for (String bad : List.of("+999999999-12-31T00:00:00Z", "-999999999-01-01T00:00:00Z")) {
            var body = new java.util.HashMap<>(tx(id(), id(), "10", "d", "l", "r"));
            body.put("occurredAt", bad);
            assertThat(service.postForEntity("/fraud/evaluate", body, String.class).getStatusCode())
                    .as("occurredAt %s", bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(velocityCircuitBreaker.getState().name()).isEqualTo("CLOSED");
    }

    @Test
    @Order(13)
    void ruleParamsRejectValuesThatWouldDisableOrFloodTheRule() {
        var admin = http.withBasicAuth("admin", ADMIN_PW);
        for (Map<String, Object> bad : List.of(
                Map.<String, Object>of("windowSeconds", 0),
                Map.<String, Object>of("windowSeconds", 999999999),
                Map.<String, Object>of("multiplier", 0),
                Map.<String, Object>of("maxTransactions", 0))) {
            assertThat(admin.exchange("/admin/rules/VELOCITY", HttpMethod.PUT,
                    comCsrf(Map.of("params", bad)), String.class).getStatusCode())
                    .as("params %s", bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @Order(14)
    void simulatorIsPublicButTheApiBehindItStaysProtected() throws Exception {
        for (String path : List.of("/simulador", "/simulador/", "/simulador/index.html", "/simulador/app.js",
                "/simulador/vistas.js", "/simulador/demo.js", "/simulador/style.css")) {
            var resp = http.getForEntity(path, String.class);
            assertThat(resp.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        }
        var page = http.getForEntity("/simulador/", String.class);
        assertThat(page.getBody()).contains("FraudGuard");
        assertThat(page.getHeaders().getFirst("Content-Security-Policy"))
                .contains("script-src 'self'").contains("frame-ancestors 'none'").doesNotContain("unsafe-inline");
        assertThat(page.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");

        assertThat(http.postForEntity("/fraud/evaluate", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/cases", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(15)
    void simulatorAssetsHaveNoInlineScriptsExternalResourcesOrUnsafeDomWrites() throws Exception {
        String html = new String(new org.springframework.core.io.ClassPathResource("static/simulador/index.html")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String js = new String(new org.springframework.core.io.ClassPathResource("static/simulador/app.js")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                + new String(new org.springframework.core.io.ClassPathResource("static/simulador/vistas.js")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                + new String(new org.springframework.core.io.ClassPathResource("static/simulador/demo.js")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).doesNotContainPattern("(?i)<script(?![^>]*\\bsrc=)")
                .doesNotContainPattern("(?i)\\son[a-z]+\\s*=").doesNotContain("http://").doesNotContain("https://");
        assertThat(js).doesNotContain("innerHTML").doesNotContain("outerHTML").doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write").doesNotContain("eval(").doesNotContain("localStorage")
                .doesNotContain("sessionStorage").doesNotContain("document.cookie");
    }

    @Test
    @Order(16)
    void csrfTokenIsRequiredForBrowserFacingWritesButNotForTheServiceApi() {
        var analyst = http.withBasicAuth("analista", ANALYST_PW);
        var admin = http.withBasicAuth("admin", ADMIN_PW);

        assertThat(analyst.postForEntity("/cases/1/approve", Map.of(), String.class).getStatusCode())
                .as("sem token CSRF").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(admin.exchange("/admin/rules/VELOCITY", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", true)), String.class).getStatusCode())
                .as("sem token CSRF").isEqualTo(HttpStatus.FORBIDDEN);

        var divergente = new org.springframework.http.HttpHeaders();
        divergente.add(org.springframework.http.HttpHeaders.COOKIE, "XSRF-TOKEN=um-valor");
        divergente.add("X-XSRF-TOKEN", "outro-valor");
        divergente.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(analyst.exchange("/cases/1/approve", HttpMethod.POST, new HttpEntity<>(Map.of(), divergente), String.class)
                .getStatusCode()).as("cookie diferente do cabeçalho").isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(evaluate(tx(id(), id(), "10", "d", "l", "r")).transactionId()).isNotBlank();
    }

    @Test
    @Order(17)
    void csrfEndpointIssuesAnHttpOnlySameSiteStrictCookie() {
        var resposta = http.exchange("/csrf", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, String>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsKey("token").containsEntry("headerName", "X-XSRF-TOKEN");
        String cookie = resposta.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("XSRF-TOKEN=").contains("HttpOnly").contains("SameSite=Strict");
    }

    @Test
    @Order(10)
    void actuatorIsNotServedOnThePublicPort() {
        assertThat(http.getForEntity("/actuator/prometheus", String.class).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode().is2xxSuccessful()).isFalse();
    }

    /** Fail-safe de verdade: congela o Redis (as chamadas estouram o timeout de 100 ms). */
    @Test
    @Order(99)
    void redisDownAppliesFailSafePolicy() {
        String c = id();
        profiles.save(new CustomerProfile(c, new BigDecimal("100"), null));
        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            var low = evaluate(tx(id(), c, "50", "d1", "BR-SP", "r1"));
            assertThat(low.outcome()).isEqualTo(Outcome.APROVAR);
            assertThat(low.reasons()).extracting(RuleHit::code).contains(FailSafePolicy.CODE);

            var high = evaluate(tx(id(), c, "5000", "d1", "BR-SP", "r1"));
            assertThat(high.reasons()).extracting(RuleHit::code).contains(FailSafePolicy.CODE);
            assertThat(high.outcome()).isEqualTo(Outcome.REVISAR);
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }
}
