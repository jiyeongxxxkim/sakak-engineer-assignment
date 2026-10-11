package foodapi.common;

import foodapi.food.DuplicateFoodCodeException;
import foodapi.food.FoodNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 계층만 올리고, 일부러 예외를 던지는 임시 컨트롤러로 핸들러의 변환 결과를 확인한다.
// 중첩 클래스는 컴포넌트 스캔에서 제외되므로 @Import로 등록한다.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        record Body(@NotBlank String foodName, @DecimalMin("0") BigDecimal calorie) {}

        @GetMapping("/test/not-found")
        void notFound() {
            throw new FoodNotFoundException(99L);
        }

        @GetMapping("/test/duplicate")
        void duplicate() {
            throw new DuplicateFoodCodeException("D000006");
        }

        @GetMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("Duplicate entry 'x' for key 'uk_food_cd'");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("비밀 내부 정보 jdbc:mysql://secret");
        }

        @GetMapping("/test/{id}")
        void byId(@PathVariable Long id) {
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Body body) {
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FoodNotFoundException은 404와 FOOD_NOT_FOUND로 변환된다")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("식품을 찾을 수 없습니다. id=99"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("DuplicateFoodCodeException은 409와 DUPLICATE_FOOD_CODE로 변환된다")
    void duplicate() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_FOOD_CODE"));
    }

    @Test
    @DisplayName("DB 무결성 위반(동시 요청 안전망)도 409로 변환된다")
    void dataIntegrity() throws Exception {
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FOOD_CODE"));
    }

    @Test
    @DisplayName("검증 실패는 400이고 필드 이름이 snake_case로 내려간다")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"food_name\":\"\",\"calorie\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='food_name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='calorie')]").exists());
    }

    @Test
    @DisplayName("깨진 JSON은 400이다")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("숫자 필드에 문자가 들어오면 400이다")
    void wrongTypeInBody() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"food_name\":\"a\",\"calorie\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("경로 변수 타입이 틀리면 400이다")
    void pathVariableTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("'id' 파라미터의 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("예상 못한 오류는 500이고 내부 메시지가 응답에 노출되지 않는다")
    void unexpectedErrorDoesNotLeak() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405이다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415이다")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404이다")
    void unknownPath() throws Exception {
        mockMvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound());
    }
}
