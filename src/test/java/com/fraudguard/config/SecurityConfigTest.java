package com.fraudguard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;

class SecurityConfigTest {

    private static final String STRONG = "a-strong-password-123";
    private final SecurityConfig config = new SecurityConfig();

    private UserDetailsService users() {
        var props = new FraudProperties(null, null, null, null, new FraudProperties.Security(STRONG, STRONG + "x", STRONG + "y"));
        return config.users(props, config.passwordEncoder());
    }

    @Test
    void userServiceNeverRewritesStoredPasswords() {
        var users = users();
        assertThat(users).isNotInstanceOf(UserDetailsPasswordService.class);
        assertThat(users.loadUserByUsername("service").getPassword()).isEqualTo("{noop}" + STRONG);
    }

    @Test
    void analystAndAdminPasswordsAreStoredHashed() {
        var users = users();
        for (String name : new String[] {"analista", "admin"}) {
            String stored = users.loadUserByUsername(name).getPassword();
            assertThat(stored).startsWith("{bcrypt}").doesNotContain(STRONG);
            assertThat(config.passwordEncoder().matches(name.equals("admin") ? STRONG + "y" : STRONG + "x", stored)).isTrue();
        }
    }

    @Test
    void shortOrBlankPasswordsFailValidation() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var weak = new FraudProperties(null, null, null, null, new FraudProperties.Security("curta", "", STRONG));
        assertThat(validator.validate(weak.security()))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("servicePassword", "analystPassword");
        assertThat(validator.validate(new FraudProperties.Security(STRONG, STRONG, STRONG))).isEmpty();
    }

    @Test
    void thresholdsMustBeOrderedAndPasswordsFitBcrypt() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new FraudProperties.Thresholds(40, 80))).isEmpty();
        for (var bad : java.util.List.of(new FraudProperties.Thresholds(0, 80), new FraudProperties.Thresholds(80, 40),
                new FraudProperties.Thresholds(40, 101))) {
            assertThat(validator.validate(bad)).as("%s", bad).isNotEmpty();
        }
        String tooLong = "x".repeat(73);
        assertThat(validator.validate(new FraudProperties.Security(STRONG, tooLong, STRONG))).isNotEmpty();
        assertThat(validator.validate(new FraudProperties.Security(tooLong, STRONG, STRONG))).isEmpty();
    }

    @Test
    void requestSizeFilterBoundsDeclaredAndChunkedBodies() throws Exception {
        var filter = new RequestSizeFilter();
        FilterChain chain = (req, res) -> {};

        var big = new MockHttpServletRequest("POST", "/fraud/evaluate");
        big.setContent(new byte[(int) RequestSizeFilter.MAX_BODY_BYTES + 1]);
        var bigResponse = new MockHttpServletResponse();
        filter.doFilter(big, bigResponse, chain);
        assertThat(bigResponse.getStatus()).isEqualTo(413);

        var chunked = new MockHttpServletRequest("POST", "/fraud/evaluate") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        chunked.setContent(new byte[(int) RequestSizeFilter.MAX_BODY_BYTES + 1]);
        FilterChain readAll = (req, res) -> req.getInputStream().readAllBytes();
        assertThatThrownBy(() -> filter.doFilter(chunked, new MockHttpServletResponse(), readAll))
                .isInstanceOf(java.io.IOException.class);

        var ok = new MockHttpServletRequest("POST", "/fraud/evaluate");
        ok.setContent(new byte[100]);
        var okResponse = new MockHttpServletResponse();
        filter.doFilter(ok, okResponse, chain);
        assertThat(okResponse.getStatus()).isEqualTo(200);

        var get = new MockHttpServletRequest("GET", "/cases");
        var getResponse = new MockHttpServletResponse();
        filter.doFilter(get, getResponse, chain);
        assertThat(getResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void unknownUserIsRejected() {
        assertThatThrownBy(() -> users().loadUserByUsername("root")).isInstanceOf(Exception.class);
    }
}
