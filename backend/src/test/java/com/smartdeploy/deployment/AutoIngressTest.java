package com.smartdeploy.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Adresse publique automatique : unique par environnement, recalculée à la promotion, jamais imposée. */
class AutoIngressTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String DOMAIN = "192.168.85.129.nip.io";

    private static ObjectNode config(String json) throws Exception {
        return (ObjectNode) M.readTree(json);
    }

    @Test
    void eachEnvironmentGetsItsOwnAddress() {
        assertEquals("test-demo-complete.192.168.85.129.nip.io", AutoIngress.host("sd-test-demo-complete", DOMAIN));
    }

    @Test
    void anApplicationWithoutAddressReceivesOne() throws Exception {
        ObjectNode c = config("{\"frontend\":{\"name\":\"demo-frontend\"}}");
        AutoIngress.apply(c, "sd-dev-demo", DOMAIN);
        assertEquals("dev-demo.192.168.85.129.nip.io", c.path("ingress").path("host").asText());
    }

    @Test
    void aPromotionDoesNotKeepTheAddressOfItsSourceEnvironment() throws Exception {
        ObjectNode c = config("{\"backend\":{\"name\":\"api\"},\"ingress\":{\"host\":\"test-demo.192.168.85.129.nip.io\"}}");
        AutoIngress.apply(c, "sd-prod-demo", DOMAIN);
        assertEquals("prod-demo.192.168.85.129.nip.io", c.path("ingress").path("host").asText());
    }

    @Test
    void anAddressChosenByHandIsKept() throws Exception {
        ObjectNode c = config("{\"backend\":{\"name\":\"api\"},\"ingress\":{\"host\":\"api.example.com\"}}");
        AutoIngress.apply(c, "sd-prod-demo", DOMAIN);
        assertEquals("api.example.com", c.path("ingress").path("host").asText());
    }

    @Test
    void nothingHappensWhenTheFeatureIsOff() throws Exception {
        ObjectNode c = config("{\"backend\":{\"name\":\"api\"}}");
        AutoIngress.apply(c, "sd-prod-demo", "");
        assertFalse(c.has("ingress"));
    }
}
