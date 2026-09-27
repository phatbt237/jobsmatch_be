package vn.career.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.auth.application.AccountAccessApi;
import vn.career.auth.infrastructure.ParentalConsentRepository;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractIntegrationTest;

class ParentLinkIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ParentalConsentRepository consents;
    @Autowired
    private AuditLogRepository auditLogs;
    @Autowired
    private AccountAccessApi accountAccess;
    @Autowired
    private JdbcTemplate jdbc;

    private String code(String studentToken) throws Exception {
        MvcResult invite = post("/api/v1/parent/invites", Map.of(), studentToken);
        assertThat(invite.getResponse().getStatus()).isEqualTo(200);
        return json(invite).get("data").get("inviteCode").asText();
    }

    private MvcResult link(String parentToken, String inviteCode, boolean consent) throws Exception {
        return post("/api/v1/parent/links", Map.of("inviteCode", inviteCode, "consent", consent), parentToken);
    }

    private UUID id(JsonNode session) {
        return UUID.fromString(session.get("user").get("id").asText());
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    @Test
    void parentRedeemingInviteApprovesLinkAndGrantsConsent() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(15));
        JsonNode parent = registerAndLogin("PARENT", null);
        UUID studentId = id(student);
        UUID parentId = id(parent);
        assertThat(student.get("user").get("status").asText()).isEqualTo("PENDING_PARENT_CONSENT");
        assertThat(accountAccess.canUseAiFeatures(studentId)).isFalse();
        assertThat(accountAccess.isApprovedParentOf(parentId, studentId)).isFalse();

        String inviteCode = code(student.get("accessToken").asText());
        assertThat(inviteCode).hasSize(8);
        MvcResult linked = link(parent.get("accessToken").asText(), inviteCode, true);

        assertThat(linked.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = json(linked).get("data");
        assertThat(data.get("studentId").asText()).isEqualTo(studentId.toString());
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("linkedAt").asText()).isNotBlank();

        // student can now use AI features, and their own /me reflects it
        assertThat(accountAccess.canUseAiFeatures(studentId)).isTrue();
        assertThat(accountAccess.isApprovedParentOf(parentId, studentId)).isTrue();
        MvcResult me = get("/api/v1/me", student.get("accessToken").asText());
        assertThat(json(me).get("data").get("status").asText()).isEqualTo("ACTIVE");

        // consent evidence was stored
        var storedConsents = consents.findByStudentId(studentId);
        assertThat(storedConsents).hasSize(1);
        assertThat(storedConsents.get(0).getMethod()).isEqualTo("PARENT_INVITE_CODE");
        assertThat(storedConsents.get(0).getParent().getId()).isEqualTo(parentId);
        assertThat(storedConsents.get(0).getConsentedAt()).isNotNull();

        // and the approval was audited with the parent as actor
        UUID linkId = jdbc.queryForObject(
                "select id from parent_student_links where invite_code = ?", UUID.class, inviteCode);
        var audit = auditLogs.findByEntityTypeAndEntityId("ParentStudentLink", linkId);
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo("PARENT_LINK_APPROVED");
        assertThat(audit.get(0).getActorId()).isEqualTo(parentId);
        assertThat(audit.get(0).getMetadata()).containsEntry("studentId", studentId.toString());
    }

    @Test
    void parentSeesLinkedChildrenOnly() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(17));
        JsonNode parent = registerAndLogin("PARENT", null);
        JsonNode otherParent = registerAndLogin("PARENT", null);
        link(parent.get("accessToken").asText(), code(student.get("accessToken").asText()), true);

        JsonNode mine = json(get("/api/v1/parent/students", parent.get("accessToken").asText())).get("data");
        JsonNode theirs = json(get("/api/v1/parent/students", otherParent.get("accessToken").asText())).get("data");

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("studentId").asText()).isEqualTo(id(student).toString());
        assertThat(mine.get(0).has("email")).isFalse();
        assertThat(theirs).isEmpty();
        assertThat(accountAccess.isApprovedParentOf(id(otherParent), id(student))).isFalse();
    }

    @Test
    void linkingAnAlreadyActiveStudentKeepsThemActive() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(17));
        JsonNode parent = registerAndLogin("PARENT", null);

        MvcResult linked = link(parent.get("accessToken").asText(), code(student.get("accessToken").asText()), true);

        assertThat(json(linked).get("data").get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void requestingAnInviteTwiceReturnsTheSameCode() throws Exception {
        String token = registerAndLogin("STUDENT", yearsAgo(17)).get("accessToken").asText();

        assertThat(code(token)).isEqualTo(code(token));
    }

    @Test
    void inviteCodeCanOnlyBeUsedOnce() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(15));
        String inviteCode = code(student.get("accessToken").asText());
        link(registerAndLogin("PARENT", null).get("accessToken").asText(), inviteCode, true);

        assertError(link(registerAndLogin("PARENT", null).get("accessToken").asText(), inviteCode, true),
                422, "INVITE_ALREADY_USED");
    }

    @Test
    void inviteCodeIsCaseInsensitive() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(17));
        String inviteCode = code(student.get("accessToken").asText());

        MvcResult linked = link(registerAndLogin("PARENT", null).get("accessToken").asText(),
                " " + inviteCode.toLowerCase() + " ", true);

        assertThat(linked.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unknownInviteCodeReturns404() throws Exception {
        String parentToken = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(link(parentToken, "ZZZZZZZZ", true), 404, "INVITE_NOT_FOUND");
    }

    @Test
    void expiredInviteCodeIsRejected() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(15));
        String inviteCode = code(student.get("accessToken").asText());
        jdbc.update("update parent_student_links set invite_expires_at = now() - interval '1 day' "
                + "where invite_code = ?", inviteCode);

        assertError(link(registerAndLogin("PARENT", null).get("accessToken").asText(), inviteCode, true),
                422, "INVITE_EXPIRED");
        // the student is still waiting for consent
        assertThat(accountAccess.canUseAiFeatures(id(student))).isFalse();
    }

    @Test
    void expiredInviteIsReplacedByANewOne() throws Exception {
        String token = registerAndLogin("STUDENT", yearsAgo(15)).get("accessToken").asText();
        String first = code(token);
        jdbc.update("update parent_student_links set invite_expires_at = now() - interval '1 day' "
                + "where invite_code = ?", first);

        assertThat(code(token)).isNotEqualTo(first);
    }

    @Test
    void linkingWithoutConsentIsRejected() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(15));
        String inviteCode = code(student.get("accessToken").asText());
        String parentToken = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(link(parentToken, inviteCode, false), 400, "VALIDATION_ERROR");
        // the code was not consumed and the student is still pending
        assertThat(accountAccess.canUseAiFeatures(id(student))).isFalse();
        assertThat(link(parentToken, inviteCode, true).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sameParentCannotLinkTheSameStudentTwice() throws Exception {
        JsonNode student = registerAndLogin("STUDENT", yearsAgo(17));
        JsonNode parent = registerAndLogin("PARENT", null);
        link(parent.get("accessToken").asText(), code(student.get("accessToken").asText()), true);
        // a second invite for the same student, redeemed by the same parent
        String secondCode = code(student.get("accessToken").asText());

        assertError(link(parent.get("accessToken").asText(), secondCode, true), 422, "ALREADY_LINKED");
    }
}
