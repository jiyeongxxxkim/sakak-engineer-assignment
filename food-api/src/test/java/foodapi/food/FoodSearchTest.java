package foodapi.food;

import foodapi.load.FoodSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class FoodSearchTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FoodSeedService seedService;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE food");
        insert("D001", "김치 찌개", 2019, "충주 Farm");
        insert("D002", "Kimchi 볶음밥", 2019, "서울");
        insert("D003", "된장국", 2020, "충주");
        insert("D004", "100% 순수 주스", 2019, "부산_본점");
        insert("D005", "김치전", 2018, null);
        insert("E006", "김밥", 2020, "서울 마포");
    }

    private void insert(String code, String name, int year, String maker) {
        jdbc.update("INSERT INTO food (food_cd, food_name, food_name_search, research_year, maker_name, maker_name_search) VALUES (?,?,?,?,?,?)",
                code, name, SearchNormalizer.normalize(name), year, maker, SearchNormalizer.normalize(maker));
    }

    // 파라미터는 URL 문자열이 아니라 .param()으로 넘겨서 인코딩 문제 없이 값을 그대로 전달한다.
    private ResultActions search(String... nameValuePairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/foods");
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions ok(int total, List<String> codes, String... nameValuePairs) throws Exception {
        ResultActions result = search(nameValuePairs)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(total));
        if (codes.isEmpty()) {
            return result.andExpect(jsonPath("$.content").isEmpty());
        }
        return result.andExpect(jsonPath("$.content[*].food_cd", contains(codes.toArray())));
    }

    @Test
    @DisplayName("조건이 없으면 전체를 id 오름차순으로 돌려주고 기본 크기는 20이다")
    void noFilter() throws Exception {
        ok(6, List.of("D001", "D002", "D003", "D004", "D005", "E006"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.content[0].food_name_search").doesNotExist());
    }

    @Test
    @DisplayName("research_year는 정확히 일치한다")
    void researchYear() throws Exception {
        ok(3, List.of("D001", "D002", "D004"), "research_year", "2019");
        ok(0, List.of(), "research_year", "2021");
    }

    @Test
    @DisplayName("food_code는 정확히 일치하고 대소문자와 공백을 무시한다")
    void foodCode() throws Exception {
        ok(1, List.of("D001"), "food_code", "D001");
        ok(1, List.of("D001"), "food_code", "d001");
        ok(1, List.of("D001"), "food_code", " D 001 ");
        ok(0, List.of(), "food_code", "D00"); // 부분 일치는 하지 않는다
    }

    @Test
    @DisplayName("food_name은 부분 일치이고 공백과 대소문자를 무시한다")
    void foodName() throws Exception {
        ok(2, List.of("D001", "D005"), "food_name", "김치");
        ok(1, List.of("D001"), "food_name", "김치찌개");        // 저장값의 공백 무시
        ok(2, List.of("D001", "D005"), "food_name", "김 치");   // 입력값의 공백 무시
        ok(1, List.of("D002"), "food_name", "KIMCHI");          // 대소문자 무시
        ok(0, List.of(), "food_name", "없는이름");
    }

    @Test
    @DisplayName("maker_name은 부분 일치이고 공백을 무시한다")
    void makerName() throws Exception {
        ok(2, List.of("D001", "D003"), "maker_name", "충주");
        ok(1, List.of("E006"), "maker_name", "서울마포");
        ok(1, List.of("E006"), "maker_name", "서울 마포");
        ok(1, List.of("D001"), "maker_name", "FARM");
    }

    @Test
    @DisplayName("여러 조건은 AND로 결합한다")
    void combined() throws Exception {
        ok(1, List.of("D001"), "food_name", "김치", "research_year", "2019");
        ok(1, List.of("D001"), "food_name", "김치", "maker_name", "충주");
        ok(1, List.of("D002"), "research_year", "2019", "maker_name", "서울");
        ok(0, List.of(), "food_name", "김치", "research_year", "2020");
    }

    @Test
    @DisplayName("LIKE 와일드카드(%, _)는 문자 그대로 검색한다")
    void wildcardsAreLiteral() throws Exception {
        ok(1, List.of("D004"), "food_name", "%");   // '%'는 모든 문자열이 아니다
        ok(0, List.of(), "food_name", "_");          // '_'는 모든 글자가 아니다
        ok(1, List.of("D004"), "maker_name", "_");   // '부산_본점'
        ok(0, List.of(), "food_name", "\\");          // 역슬래시
    }

    @Test
    @DisplayName("값이 비어 있거나 공백뿐인 파라미터는 조건이 없는 것으로 본다")
    void blankParamsAreIgnored() throws Exception {
        ok(6, List.of("D001", "D002", "D003", "D004", "D005", "E006"),
                "food_name", "", "maker_name", "  ", "food_code", "");
    }

    @Test
    @DisplayName("결과가 없으면 200과 빈 목록이다")
    void emptyResult() throws Exception {
        search("food_name", "없는이름")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.total_pages").value(0));
    }

    @Test
    @DisplayName("page와 size로 나눠 조회하고 범위를 벗어난 page는 빈 목록이다")
    void paging() throws Exception {
        search("size", "2", "page", "0")
                .andExpect(jsonPath("$.content[*].food_cd", contains("D001", "D002")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total_elements").value(6))
                .andExpect(jsonPath("$.total_pages").value(3));
        search("size", "2", "page", "2")
                .andExpect(jsonPath("$.content[*].food_cd", contains("D005", "E006")));
        search("size", "2", "page", "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.total_elements").value(6));
    }

    @Test
    @DisplayName("size는 최대 100으로 제한되고 잘못된 page와 size는 기본값으로 보정된다")
    void sizeIsCapped() throws Exception {
        search("size", "1000").andExpect(jsonPath("$.size").value(100));
        search("size", "0").andExpect(jsonPath("$.size").value(20));
        search("page", "-1").andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("sort 파라미터는 무시하고 id 오름차순을 유지한다")
    void sortIsIgnored() throws Exception {
        search("sort", "food_name,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].food_cd").value("D001"));
        search("sort", "존재하지않는컬럼,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].food_cd").value("D001"));
    }

    @Test
    @DisplayName("research_year가 숫자가 아니거나 범위를 넘으면 400이다")
    void invalidResearchYear() throws Exception {
        search("research_year", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        search("research_year", "99999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("원본 엑셀을 적재한 데이터에서도 검색 결과 건수가 DB와 일치한다")
    void searchOnRealData() throws Exception {
        jdbc.execute("TRUNCATE TABLE food");
        seedService.load(Path.of("data/food_nutrition.xlsx"));

        search("research_year", "2019").andExpect(jsonPath("$.total_elements").value(6759));
        search("research_year", "2020").andExpect(jsonPath("$.total_elements").value(863));
        search("food_code", "d000006").andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].food_name").value("꿩불고기"));

        long expectedMaker = jdbc.queryForObject("SELECT COUNT(*) FROM food WHERE maker_name_search LIKE '%충주%'", Long.class);
        long expectedName = jdbc.queryForObject("SELECT COUNT(*) FROM food WHERE food_name_search LIKE '%김치찌개%'", Long.class);
        assertThat(expectedMaker).isPositive();
        assertThat(expectedName).isPositive();
        search("maker_name", "충주").andExpect(jsonPath("$.total_elements").value(expectedMaker));
        search("food_name", "김치 찌개", "size", "100").andExpect(jsonPath("$.total_elements").value(expectedName));
        search("size", "100", "page", "76").andExpect(jsonPath("$.content.length()").value(83)).andExpect(jsonPath("$.total_pages").value(77));
    }
}
