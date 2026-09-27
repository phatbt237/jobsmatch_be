package vn.career;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.AbstractIntegrationTest;

/** The health endpoint used by Docker and load balancers, and that nothing else of Actuator is exposed. */
class HealthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthIsPublicAndReportsUpWithoutInternalDetails() throws Exception {
        MvcResult result = get("/actuator/health", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.has("components")).as("no details for anonymous callers").isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("jdbc:", "postgres", "redis");
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        assertThat(get("/actuator/health/liveness", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        String admin = adminToken();

        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics"}) {
            assertThat(get(path, null).getResponse().getStatus()).as(path + " anonymous").isEqualTo(401);
            assertThat(get(path, admin).getResponse().getStatus()).as(path + " even for an admin").isEqualTo(404);
        }
    }
}
