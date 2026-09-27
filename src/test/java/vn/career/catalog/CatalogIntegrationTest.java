package vn.career.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.AbstractIntegrationTest;

class CatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    private JsonNode data(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json(result).get("data");
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    private String majorId(String code) {
        return jdbc.queryForObject("select id::text from majors where code = ?", String.class, code);
    }

    private static Set<String> codesIn(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false).map(i -> i.get("code").asText()).collect(Collectors.toSet());
    }

    // ---------- seed ----------

    @Test
    void seedHasThirtyMajorsEachWithAProfileAndSampleAdmissionData() {
        assertThat(jdbc.queryForObject("select count(*) from majors where code in "
                + "('IT','DS','CYBERSEC','ECOMMERCE','ACCOUNTING','MARKETING','BUSINESS','FINANCE','INTLECON','LOGISTICS',"
                + "'MEDICINE','NURSING','PHARMACY','BIOTECH','FOODTECH','LAW','PSYCHOLOGY','SOCIALWORK','EDUCATION',"
                + "'ENGLISH','JOURNALISM','PR','MULTIMEDIA','GRAPHICDESIGN','ARCHITECTURE','MECHANICAL','ELECTRICAL',"
                + "'CIVIL','AUTOMOTIVE','TOURISM')", Integer.class)).isEqualTo(30);
        // 19 dimensions minus the zero weights that were left out: at least 8 per major
        assertThat(jdbc.queryForObject("select count(*) from (select major_id from major_profiles group by major_id "
                + "having count(*) >= 8) t", Integer.class)).isGreaterThanOrEqualTo(30);
        assertThat(jdbc.queryForObject("select count(*) from university_majors where not is_sample", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from universities where not is_sample", Integer.class)).isZero();
        // every seeded major is offered by at least two universities in every year
        assertThat(jdbc.queryForObject("select count(*) from (select major_id, year from university_majors "
                + "group by major_id, year having count(distinct university_id) < 2) t", Integer.class)).isZero();
    }

    @Test
    void seededCutoffsAndTuitionAreWithinPlausibleRanges() {
        assertThat(jdbc.queryForObject("select count(*) from university_majors where cutoff_score < 10 or cutoff_score > 30",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select min(tuition_per_year) from university_majors", Long.class)).isPositive();
    }

    // ---------- public access ----------

    @Test
    void catalogIsPublicAndPaged() throws Exception {
        JsonNode page = data(get("/api/v1/majors?size=10", null), 200);

        assertThat(page.get("items")).hasSize(10);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(10);
        assertThat(page.get("totalItems").asInt()).isGreaterThanOrEqualTo(30);
        assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(page.get("items").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "code", "name", "groupName", "shortDescription", "combos");
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        assertThat(data(get("/api/v1/majors?size=5000", null), 200).get("size").asInt()).isEqualTo(100);
    }

    @Test
    void majorsCanBeFilteredByGroupComboAndText() throws Exception {
        JsonNode it = data(get("/api/v1/majors?group=" + encode("Công nghệ thông tin") + "&size=100", null), 200);
        assertThat(codesIn(it.get("items"))).containsExactlyInAnyOrder("IT", "DS", "CYBERSEC");

        JsonNode b00 = data(get("/api/v1/majors?combo=b00&size=100", null), 200);
        assertThat(codesIn(b00.get("items"))).contains("MEDICINE", "NURSING", "PHARMACY").doesNotContain("IT", "LAW");

        JsonNode search = data(get("/api/v1/majors?q=" + encode("Y ĐA KHOA"), null), 200);
        assertThat(codesIn(search.get("items"))).containsExactly("MEDICINE");

        JsonNode combined = data(get("/api/v1/majors?combo=D01&q=" + encode("kinh") + "&size=100", null), 200);
        assertThat(codesIn(combined.get("items"))).contains("BUSINESS", "INTLECON").doesNotContain("MEDICINE");

        assertThat(data(get("/api/v1/majors?q=zzzzzz", null), 200).get("items")).isEmpty();
    }

    /** MockMvc percent-encodes the URI template itself, so values must be passed as they are (encoding twice breaks them). */
    private static String encode(String value) {
        return value;
    }

    @Test
    void majorDetailShowsThreeYearsOfSampleAdmissionDataPerUniversity() throws Exception {
        JsonNode detail = data(get("/api/v1/majors/" + majorId("IT"), null), 200);

        assertThat(detail.get("name").asText()).isEqualTo("Công nghệ thông tin");
        assertThat(detail.get("typicalJobs")).isNotEmpty();
        assertThat(detail.get("combos")).extracting(JsonNode::asText).contains("A00", "A01", "D01");
        assertThat(detail.get("sampleData").asBoolean()).as("seed data must be flagged as sample").isTrue();
        JsonNode universities = detail.get("universities");
        assertThat(universities.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode uni : universities) {
            assertThat(uni.get("sampleData").asBoolean()).isTrue();
            assertThat(uni.get("years")).hasSize(3);
            assertThat(uni.get("years").get(0).get("year").asInt()).isGreaterThan(uni.get("years").get(2).get("year").asInt());
            assertThat(uni.get("years").get(0).get("cutoffScore").decimalValue()).isBetween(
                    java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(30));
            assertThat(uni.get("years").get(0).get("tuitionPerYear").asLong()).isPositive();
        }
    }

    @Test
    void unknownMajorIsNotFound() throws Exception {
        assertError(get("/api/v1/majors/" + UUID.randomUUID(), null), 404, "MAJOR_NOT_FOUND");
        assertError(get("/api/v1/majors/not-a-uuid", null), 400, "MALFORMED_REQUEST");
    }

    // ---------- compare ----------

    @Test
    void compareReturnsSideBySideFactsInTheRequestedOrder() throws Exception {
        String ids = majorId("MEDICINE") + "," + majorId("IT") + "," + majorId("LAW");

        JsonNode majors = data(get("/api/v1/majors/compare?ids=" + ids, null), 200).get("majors");

        assertThat(majors).hasSize(3);
        assertThat(majors.get(0).get("code").asText()).isEqualTo("MEDICINE");
        assertThat(majors.get(1).get("code").asText()).isEqualTo("IT");
        JsonNode med = majors.get(0);
        assertThat(med.get("latestYear").asInt()).isEqualTo(2025);
        assertThat(med.get("minCutoffScore").decimalValue()).isLessThanOrEqualTo(med.get("maxCutoffScore").decimalValue());
        assertThat(med.get("universityCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(med.get("sampleData").asBoolean()).isTrue();
    }

    @Test
    void compareAcceptsAtMostThreeDistinctMajors() throws Exception {
        String a = majorId("IT");
        String b = majorId("LAW");
        String c = majorId("MEDICINE");
        String d = majorId("CIVIL");

        assertError(get("/api/v1/majors/compare?ids=" + a + "," + b + "," + c + "," + d, null), 400, "VALIDATION_ERROR");
        // duplicates collapse, so four ids with a repeat are fine
        assertThat(data(get("/api/v1/majors/compare?ids=" + a + "," + a + "," + b + "," + c, null), 200).get("majors"))
                .hasSize(3);
        assertError(get("/api/v1/majors/compare", null), 400, "MALFORMED_REQUEST");
        assertError(get("/api/v1/majors/compare?ids=" + UUID.randomUUID(), null), 404, "MAJOR_NOT_FOUND");
    }

    // ---------- universities ----------

    @Test
    void universitiesCanBeFilteredByRegion() throws Exception {
        assertThat(data(get("/api/v1/universities?region=NORTH", null), 200)).hasSize(3);
        assertThat(data(get("/api/v1/universities?region=central", null), 200)).hasSize(2);
        JsonNode all = data(get("/api/v1/universities", null), 200);
        assertThat(all.size()).isGreaterThanOrEqualTo(8);
        assertThat(all.get(0).get("sampleData").asBoolean()).isTrue();
        assertError(get("/api/v1/universities?region=MARS", null), 400, "VALIDATION_ERROR");
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        assertError(post("/api/v1/majors", Map.of("name", "x"), null), 401, "UNAUTHORIZED");
    }

    // ---------- cache ----------

    @Test
    void responsesAreCachedInRedisAndClearedWhenAnAdminChangesTheCatalog() throws Exception {
        String admin = adminToken();
        JsonNode created = data(post("/api/v1/admin/majors", Map.of("code", "CACHE_" + System.nanoTime() % 100000,
                "name", "Ngành thử cache", "groupName", "Nhóm thử cache " + UUID.randomUUID(),
                "combos", List.of("A00")), admin), 201);
        String id = created.get("id").asText();
        String group = created.get("groupName").asText();

        // first read fills the cache, second read is served from it
        JsonNode first = data(get("/api/v1/majors/" + id, null), 200);
        assertThat(redis.keys("catalog:*")).isNotEmpty();
        assertThat(data(get("/api/v1/majors/" + id, null), 200)).isEqualTo(first);
        assertThat(data(get("/api/v1/majors?group=" + encode(group), null), 200).get("items")).hasSize(1);

        // an edit must be visible immediately, not after the 1 hour TTL
        data(put("/api/v1/admin/majors/" + id, Map.of("name", "Ngành đã đổi tên", "groupName", group,
                "combos", List.of("A00")), admin), 200);
        assertThat(data(get("/api/v1/majors/" + id, null), 200).get("name").asText()).isEqualTo("Ngành đã đổi tên");
        assertThat(data(get("/api/v1/majors?group=" + encode(group), null), 200).get("items").get(0).get("name").asText())
                .isEqualTo("Ngành đã đổi tên");

        // deactivating removes it from the public catalog
        assertThat(delete("/api/v1/admin/majors/" + id, admin).getResponse().getStatus()).isEqualTo(200);
        assertError(get("/api/v1/majors/" + id, null), 404, "MAJOR_NOT_FOUND");
        assertThat(data(get("/api/v1/majors?group=" + encode(group), null), 200).get("items")).isEmpty();
    }

    @Test
    void theApiKeepsWorkingWhenRedisIsUnavailableForAKey() throws Exception {
        // Corrupt cache content must be ignored and replaced, not turned into a 500
        String id = majorId("LAW");
        data(get("/api/v1/majors/" + id, null), 200);
        for (String key : redis.keys("catalog:*")) {
            if (!key.equals("catalog:version")) {
                redis.opsForValue().set(key, "{ this is not json");
            }
        }

        assertThat(data(get("/api/v1/majors/" + id, null), 200).get("code").asText()).isEqualTo("LAW");
    }

    // ---------- admin ----------

    @Test
    void onlyAdminsCanManageTheCatalog() throws Exception {
        String student = studentToken();

        assertError(get("/api/v1/admin/majors", student), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/admin/majors", Map.of("code", "X1", "name", "x", "groupName", "y"), student), 403, "ACCESS_DENIED");
        assertError(get("/api/v1/admin/universities", null), 401, "UNAUTHORIZED");
        assertError(delete("/api/v1/admin/university-majors/" + UUID.randomUUID(), student), 403, "ACCESS_DENIED");
    }

    @Test
    void adminCanCreateAMajorWithAProfileAndItsAdmissionData() throws Exception {
        String admin = adminToken();
        String code = "ADM_" + Math.abs(System.nanoTime() % 1_000_000);
        JsonNode major = data(post("/api/v1/admin/majors", Map.of("code", code, "name", "Ngành quản trị thử",
                "groupName", "Nhóm quản trị thử", "shortDescription", "mô tả ngắn", "combos", List.of("A00", "D01"),
                "typicalJobs", List.of("Việc 1", "Việc 2")), admin), 201);
        String id = major.get("id").asText();
        assertThat(major.get("active").asBoolean()).isTrue();

        // duplicate code
        assertError(post("/api/v1/admin/majors", Map.of("code", code, "name", "again", "groupName", "g"), admin),
                422, "DUPLICATE_CODE");
        // code is required on create
        assertError(post("/api/v1/admin/majors", Map.of("name", "no code", "groupName", "g"), admin), 400, "VALIDATION_ERROR");

        // profile: unknown dimension, weight out of range, then a valid one
        assertError(put("/api/v1/admin/majors/" + id + "/profile", Map.of("weights", Map.of("NOPE", 0.5)), admin),
                400, "VALIDATION_ERROR");
        assertError(put("/api/v1/admin/majors/" + id + "/profile", Map.of("weights", Map.of("I", 1.5)), admin),
                400, "VALIDATION_ERROR");
        JsonNode withProfile = data(put("/api/v1/admin/majors/" + id + "/profile",
                Map.of("weights", Map.of("I", 0.9, "C", 0.5, "LOGIC", 0.0)), admin), 200);
        assertThat(withProfile.get("profile").get("I").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(withProfile.get("profile").has("LOGIC")).as("zero weights are not stored").isFalse();
        // replaced, not merged
        JsonNode replaced = data(put("/api/v1/admin/majors/" + id + "/profile", Map.of("weights", Map.of("A", 0.7)), admin), 200);
        assertThat(replaced.get("profile").has("I")).isFalse();
        assertThat(replaced.get("profile").get("A").decimalValue()).isEqualByComparingTo("0.7");

        // admission data
        String university = jdbc.queryForObject("select id::text from universities where code = 'UNI_N1'", String.class);
        Map<String, Object> admission = Map.of("universityId", university, "majorId", id, "year", 2025, "combo", "A00",
                "cutoffScore", 25.5, "tuitionPerYear", 30_000_000);
        JsonNode um = data(post("/api/v1/admin/university-majors", admission, admin), 201);
        assertThat(um.get("sampleData").asBoolean()).as("UNI_N1 is a sample university").isTrue();
        assertError(post("/api/v1/admin/university-majors", admission, admin), 422, "DUPLICATE_CODE");
        assertError(post("/api/v1/admin/university-majors", Map.of("year", 2025, "combo", "A00"), admin), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/admin/university-majors", Map.of("universityId", university, "majorId", id,
                "year", 2025, "combo", "A00", "cutoffScore", 31), admin), 400, "VALIDATION_ERROR");

        JsonNode updated = data(put("/api/v1/admin/university-majors/" + um.get("id").asText(),
                Map.of("year", 2025, "combo", "A00", "cutoffScore", 26.0, "tuitionPerYear", 31_000_000), admin), 200);
        assertThat(updated.get("cutoffScore").decimalValue()).isEqualByComparingTo("26.0");
        JsonNode listed = data(get("/api/v1/admin/university-majors?majorId=" + id, admin), 200);
        assertThat(listed.get("totalItems").asInt()).isEqualTo(1);

        // the public detail shows it
        JsonNode detail = data(get("/api/v1/majors/" + id, null), 200);
        assertThat(detail.get("universities")).hasSize(1);
        assertThat(detail.get("universities").get(0).get("years").get(0).get("cutoffScore").decimalValue())
                .isEqualByComparingTo("26.0");

        assertThat(delete("/api/v1/admin/university-majors/" + um.get("id").asText(), admin).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(data(get("/api/v1/majors/" + id, null), 200).get("universities")).isEmpty();
        assertError(delete("/api/v1/admin/university-majors/" + um.get("id").asText(), admin), 404, "UNIVERSITY_MAJOR_NOT_FOUND");
    }

    @Test
    void adminCanManageUniversities() throws Exception {
        String admin = adminToken();
        String code = "UNI_T" + Math.abs(System.nanoTime() % 100_000);
        JsonNode created = data(post("/api/v1/admin/universities",
                Map.of("code", code, "name", "Trường thử", "region", "CENTRAL", "type", "PRIVATE"), admin), 201);
        String id = created.get("id").asText();
        assertThat(created.get("sampleData").asBoolean()).as("universities created by admins are real data").isFalse();

        assertError(post("/api/v1/admin/universities",
                Map.of("code", code, "name", "again", "region", "CENTRAL", "type", "PRIVATE"), admin), 422, "DUPLICATE_CODE");
        assertError(post("/api/v1/admin/universities",
                Map.of("code", "UNI_BAD1", "name", "bad", "region", "MARS", "type", "PRIVATE"), admin), 400, "MALFORMED_REQUEST");

        assertThat(codesIn(data(get("/api/v1/universities?region=CENTRAL", null), 200))).contains(code);
        data(put("/api/v1/admin/universities/" + id,
                Map.of("name", "Trường thử đổi tên", "region", "SOUTH", "type", "PUBLIC"), admin), 200);
        assertThat(data(get("/api/v1/universities?region=SOUTH", null), 200).findValuesAsText("name")).contains("Trường thử đổi tên");
        assertThat(data(get("/api/v1/admin/universities", admin), 200).get("totalItems").asInt()).isGreaterThanOrEqualTo(9);

        assertThat(delete("/api/v1/admin/universities/" + id, admin).getResponse().getStatus()).isEqualTo(200);
        assertError(delete("/api/v1/admin/universities/" + id, admin), 404, "UNIVERSITY_NOT_FOUND");
    }

    @Test
    void adminMajorListIncludesInactiveMajorsAndCanSearch() throws Exception {
        String admin = adminToken();
        String code = "INACT_" + Math.abs(System.nanoTime() % 100_000);
        String id = data(post("/api/v1/admin/majors", Map.of("code", code, "name", "Ngành tạm ngưng xyz", "groupName", "g",
                "active", false), admin), 201).get("id").asText();

        JsonNode found = data(get("/api/v1/admin/majors?q=" + encode("tạm ngưng xyz"), admin), 200);

        assertThat(found.get("items")).hasSize(1);
        assertThat(found.get("items").get(0).get("active").asBoolean()).isFalse();
        assertError(get("/api/v1/majors/" + id, null), 404, "MAJOR_NOT_FOUND");
        assertThat(codesIn(data(get("/api/v1/majors?q=" + encode("tạm ngưng xyz"), null), 200).get("items"))).isEmpty();
        assertThat(data(get("/api/v1/admin/majors/" + id, admin), 200).get("code").asText()).isEqualTo(code);
    }
}
