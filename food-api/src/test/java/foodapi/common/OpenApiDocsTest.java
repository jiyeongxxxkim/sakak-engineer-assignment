package foodapi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OpenAPI 문서가 실제 API(경로, snake_case 필드, 오류 응답)와 어긋나지 않는지 확인한다.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocsTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI 문서에 모든 엔드포인트가 있다")
    void documentsAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("식품영양성분 API"))
                .andExpect(jsonPath("$.paths['/foods'].get").exists())
                .andExpect(jsonPath("$.paths['/foods'].post").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].delete").exists());
    }

    @Test
    @DisplayName("스키마의 필드 이름은 실제 JSON과 같은 snake_case이다")
    void schemasUseSnakeCase() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.FoodRequest.properties.food_cd").exists())
                .andExpect(jsonPath("$.components.schemas.FoodRequest.properties.saturated_fatty_acids").exists())
                .andExpect(jsonPath("$.components.schemas.FoodRequest.properties.foodCd").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.FoodResponse.properties.food_cd").exists())
                .andExpect(jsonPath("$.components.schemas.FoodResponse.properties.serving_unit").exists())
                .andExpect(jsonPath("$.components.schemas.PageResponseFoodResponse.properties.total_elements").exists())
                .andExpect(jsonPath("$.components.schemas.PageResponseFoodResponse.properties.total_pages").exists());
    }

    @Test
    @DisplayName("검색 파라미터와 페이지 파라미터가 풀려서 문서화된다")
    void documentsSearchParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/foods'].get.parameters[*].name",
                        hasItems("food_name", "research_year", "maker_name", "food_code", "page", "size")))
                .andExpect(jsonPath("$.paths['/foods'].get.parameters[*].name", not(hasItem("pageable"))));
    }

    @Test
    @DisplayName("오류 응답(400, 404, 409)이 문서화된다")
    void documentsErrorResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/foods'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/foods'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/foods'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/foods/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.request_id").exists());
    }

    @Test
    @DisplayName("응답 형식은 application/json으로 문서화된다")
    void documentsJsonMediaType() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/foods/{id}'].get.responses['200'].content['application/json']").exists());
    }

    @Test
    @DisplayName("Swagger UI 페이지가 열린다")
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
