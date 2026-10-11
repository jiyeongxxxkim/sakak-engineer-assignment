package foodapi.food;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class FoodControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("TRUNCATE TABLE food");
    }

    private Map<String, Object> minimal(String foodCd, String foodName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("food_cd", foodCd);
        body.put("food_name", foodName);
        return body;
    }

    private Map<String, Object> full() {
        Map<String, Object> body = minimal("T001", "김치 찌개");
        body.put("group_name", "찌개류");
        body.put("research_year", 2024);
        body.put("maker_name", "충주 Farm");
        body.put("ref_name", "테스트 출처");
        body.put("serving_size", 300);
        body.put("serving_unit", "g");
        body.put("calorie", 150.5);
        body.put("carbohydrate", 12.3);
        body.put("protein", 8.1);
        body.put("fat", 6.2);
        body.put("sugars", 2.5);
        body.put("sodium", 900);
        body.put("cholesterol", 20);
        body.put("saturated_fatty_acids", 1.5);
        body.put("trans_fat", 0.1);
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/foods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions createRaw(String json) throws Exception {
        return mockMvc.perform(post("/foods").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("생성하면 201과 Location 헤더, snake_case 응답 본문을 돌려준다")
    void createReturns201() throws Exception {
        create(full())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/foods/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.food_cd").value("T001"))
                .andExpect(jsonPath("$.group_name").value("찌개류"))
                .andExpect(jsonPath("$.food_name").value("김치 찌개"))
                .andExpect(jsonPath("$.research_year").value(2024))
                .andExpect(jsonPath("$.maker_name").value("충주 Farm"))
                .andExpect(jsonPath("$.ref_name").value("테스트 출처"))
                .andExpect(jsonPath("$.serving_size").value(300))
                .andExpect(jsonPath("$.serving_unit").value("g"))
                .andExpect(jsonPath("$.calorie").value(150.5))
                .andExpect(jsonPath("$.carbohydrate").value(12.3))
                .andExpect(jsonPath("$.protein").value(8.1))
                .andExpect(jsonPath("$.fat").value(6.2))
                .andExpect(jsonPath("$.sugars").value(2.5))
                .andExpect(jsonPath("$.sodium").value(900))
                .andExpect(jsonPath("$.cholesterol").value(20))
                .andExpect(jsonPath("$.saturated_fatty_acids").value(1.5))
                .andExpect(jsonPath("$.trans_fat").value(0.1))
                .andExpect(jsonPath("$.food_name_search").doesNotExist())
                .andExpect(jsonPath("$.maker_name_search").doesNotExist());
    }

    @Test
    @DisplayName("필수값(food_cd, food_name)만 보내도 생성되고 나머지는 null이다")
    void createWithRequiredOnly() throws Exception {
        create(minimal("T002", "밥"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.food_cd").value("T002"))
                .andExpect(jsonPath("$.calorie").doesNotExist())
                .andExpect(jsonPath("$.research_year").doesNotExist());
    }

    @Test
    @DisplayName("저장 시 food_cd 앞뒤 공백이 제거되고 검색용 값이 정규화된다")
    void normalizesOnSave() throws Exception {
        Map<String, Object> body = minimal("  T003  ", " Kimchi 찌개 ");
        body.put("maker_name", "충주 Farm");

        create(body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.food_cd").value("T003"))
                .andExpect(jsonPath("$.food_name").value("Kimchi 찌개"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM food WHERE food_cd = 'T003'");
        assertThat(row.get("food_name_search")).isEqualTo("kimchi찌개");
        assertThat(row.get("maker_name_search")).isEqualTo("충주farm");
    }

    @Test
    @DisplayName("같은 식품코드로 생성하면 409이다")
    void duplicateCodeReturns409() throws Exception {
        create(minimal("T004", "첫번째")).andExpect(status().isCreated());

        create(minimal("T004", "두번째"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_FOOD_CODE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM food", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("대소문자나 앞뒤 공백만 다른 식품코드도 중복으로 본다")
    void duplicateIgnoringCaseAndSpaces() throws Exception {
        create(minimal("D000100", "원본")).andExpect(status().isCreated());

        create(minimal("d000100", "소문자")).andExpect(status().isConflict());
        create(minimal("  D000100 ", "공백")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("필수값이 없거나 범위를 벗어나면 400이고 필드별 오류를 알려준다")
    void validationErrors() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("food_cd", "   ");
        body.put("research_year", 1800);
        body.put("calorie", -1);
        body.put("sodium", 123456789.123);
        body.put("food_name", "가".repeat(101));

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[?(@.field=='food_cd')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='food_name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='research_year')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='calorie')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='sodium')]").exists());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM food", Long.class)).isZero();
    }

    @Test
    @DisplayName("food_name이 없으면 400이다")
    void missingFoodName() throws Exception {
        createRaw("{\"food_cd\":\"T005\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='food_name')]").exists());
    }

    @Test
    @DisplayName("깨진 JSON이나 숫자 필드의 문자열은 400이다")
    void malformedBodies() throws Exception {
        createRaw("{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        createRaw("{\"food_cd\":\"T006\",\"food_name\":\"a\",\"calorie\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("생성한 식품을 id로 조회할 수 있다")
    void getReturnsCreatedFood() throws Exception {
        String location = create(full()).andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.food_cd").value("T001"))
                .andExpect(jsonPath("$.food_name").value("김치 찌개"))
                .andExpect(jsonPath("$.calorie").value(150.5))
                .andExpect(jsonPath("$.saturated_fatty_acids").value(1.5));
    }

    @Test
    @DisplayName("없는 id를 조회하면 404이다")
    void getMissingReturns404() throws Exception {
        mockMvc.perform(get("/foods/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
                .andExpect(jsonPath("$.message", endsWith("id=999999")));
    }

    @Test
    @DisplayName("id가 숫자가 아니면 400이다")
    void getWithNonNumericId() throws Exception {
        mockMvc.perform(get("/foods/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- 수정(PUT) / 삭제(DELETE) ----

    private ResultActions update(long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/foods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createAndGetId(Map<String, Object> body) throws Exception {
        String location = create(body).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM food", Long.class);
    }

    @Test
    @DisplayName("수정하면 전체가 교체되고 요청에 없는 선택 필드는 null이 된다")
    void putReplacesWholeResource() throws Exception {
        long id = createAndGetId(full());

        Map<String, Object> body = minimal("T001", "묵은지 찌개");
        body.put("calorie", 200);

        update(id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.food_name").value("묵은지 찌개"))
                .andExpect(jsonPath("$.calorie").value(200))
                .andExpect(jsonPath("$.group_name").doesNotExist())
                .andExpect(jsonPath("$.protein").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM food WHERE id = ?", id);
        assertThat(row.get("food_name")).isEqualTo("묵은지 찌개");
        assertThat(row.get("food_name_search")).isEqualTo("묵은지찌개");
        assertThat(row.get("maker_name_search")).isNull();
        assertThat(row.get("group_name")).isNull();
        assertThat(row.get("protein")).isNull();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정 후 조회하면 바뀐 값이 보인다")
    void getAfterPut() throws Exception {
        long id = createAndGetId(full());
        update(id, minimal("T001", "바뀐 이름")).andExpect(status().isOk());

        mockMvc.perform(get("/foods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.food_name").value("바뀐 이름"));
    }

    @Test
    @DisplayName("없는 id를 수정하면 404이다")
    void putMissingReturns404() throws Exception {
        update(999999, minimal("T001", "없음"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("다른 식품이 쓰는 코드로 수정하면 409이고 값은 바뀌지 않는다")
    void putWithOtherRowsCodeReturns409() throws Exception {
        createAndGetId(minimal("A001", "첫번째"));
        long secondId = createAndGetId(minimal("B001", "두번째"));

        update(secondId, minimal("a001", "두번째 수정"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FOOD_CODE"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM food WHERE id = ?", secondId);
        assertThat(row.get("food_cd")).isEqualTo("B001");
        assertThat(row.get("food_name")).isEqualTo("두번째");
    }

    @Test
    @DisplayName("자기 자신의 코드를 그대로(대소문자만 바꿔서도) 수정하는 것은 허용된다")
    void putWithOwnCodeIsAllowed() throws Exception {
        long id = createAndGetId(minimal("D000200", "원본"));

        update(id, minimal("D000200", "같은 코드")).andExpect(status().isOk());
        update(id, minimal("d000200", "소문자 코드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.food_cd").value("d000200"));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정 요청이 검증에 실패하면 400이고 값은 바뀌지 않는다")
    void putValidationErrors() throws Exception {
        long id = createAndGetId(minimal("V001", "원본"));

        Map<String, Object> body = minimal("V001", "수정");
        body.put("calorie", -5);
        update(id, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='calorie')]").exists());

        assertThat(jdbc.queryForObject("SELECT food_name FROM food WHERE id = ?", String.class, id)).isEqualTo("원본");
    }

    @Test
    @DisplayName("수정할 때 id가 숫자가 아니면 400이다")
    void putWithNonNumericId() throws Exception {
        mockMvc.perform(put("/foods/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimal("X", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("삭제하면 204이고 이후 조회하면 404이다")
    void deleteThenGetReturns404() throws Exception {
        long id = createAndGetId(full());

        mockMvc.perform(delete("/foods/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/foods/" + id))
                .andExpect(status().isNotFound());
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("삭제는 대상 식품만 지우고 다른 식품은 그대로 둔다")
    void deleteOnlyTarget() throws Exception {
        long keepId = createAndGetId(minimal("K001", "남길 것"));
        long removeId = createAndGetId(minimal("K002", "지울 것"));

        mockMvc.perform(delete("/foods/" + removeId)).andExpect(status().isNoContent());

        assertThat(count()).isEqualTo(1);
        mockMvc.perform(get("/foods/" + keepId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 id를 삭제하면 404이고, 같은 id를 두 번 삭제하면 두 번째는 404이다")
    void deleteMissingReturns404() throws Exception {
        mockMvc.perform(delete("/foods/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));

        long id = createAndGetId(minimal("DEL1", "삭제"));
        mockMvc.perform(delete("/foods/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/foods/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제할 때 id가 숫자가 아니면 400이다")
    void deleteWithNonNumericId() throws Exception {
        mockMvc.perform(delete("/foods/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
