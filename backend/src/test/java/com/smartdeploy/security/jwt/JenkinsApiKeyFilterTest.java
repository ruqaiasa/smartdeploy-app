package com.smartdeploy.security.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsApiKeyFilterTest {

    private static final String KEY = "cle-de-test-uniquement";

    private final JenkinsApiKeyFilter filter = new JenkinsApiKeyFilter(KEY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validKeyOnWebhookShouldGrantJenkinsRole() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/webhooks/jenkins");
        request.addHeader(JenkinsApiKeyFilter.API_KEY_HEADER, KEY);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_JENKINS")));
    }

    @Test
    void validKeyOnManifestExportShouldGrantJenkinsRole() throws Exception {
        MockHttpServletRequest request = request(
                "GET", "/api/kubernetes/manifests/deployments/61/export"
        );
        request.addHeader(JenkinsApiKeyFilter.API_KEY_HEADER, KEY);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidKeyShouldLeaveRequestAnonymous() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/webhooks/jenkins");
        request.addHeader(JenkinsApiKeyFilter.API_KEY_HEADER, "mauvaise-cle");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingKeyShouldLeaveRequestAnonymous() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/webhooks/jenkins");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void keyMustNotGrantAccessToOtherRoutes() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/deployments");
        request.addHeader(JenkinsApiKeyFilter.API_KEY_HEADER, KEY);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void blankConfiguredKeyShouldPreventStartup() {
        assertThrows(IllegalStateException.class, () -> new JenkinsApiKeyFilter(" "));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
