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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
