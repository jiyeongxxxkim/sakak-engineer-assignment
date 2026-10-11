package foodapi.common;

import foodapi.food.FoodNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = RequestLoggingFilterTest.TestController.class)
@Import(RequestLoggingFilterTest.TestController.class)
class RequestLoggingFilterTest {

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/test/missing")
        void missing() {
            throw new FoodNotFoundException(7L);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("내부 오류");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private String requestIdOf(MvcResult result) {
        return result.getResponse().getHeader("X-Request-Id");
    }

    @Test
    @DisplayName("응답 헤더에 요청 ID가 생성된다")
    void generatesRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok")).andExpect(status().isOk()).andReturn();

        assertThat(requestIdOf(result)).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("요청마다 다른 ID가 만들어진다")
    void idsAreUniquePerRequest() throws Exception {
        String first = requestIdOf(mockMvc.perform(get("/test/ok")).andReturn());
        String second = requestIdOf(mockMvc.perform(get("/test/ok")).andReturn());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("클라이언트가 보낸 요청 ID는 그대로 사용한다")
    void keepsClientRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok").header("X-Request-Id", "client-trace.123_A")).andReturn();

        assertThat(requestIdOf(result)).isEqualTo("client-trace.123_A");
    }

    @Test
    @DisplayName("허용하지 않는 문자나 너무 긴 요청 ID는 버리고 새로 만든다")
    void rejectsUnsafeRequestId() throws Exception {
        String withSpace = requestIdOf(mockMvc.perform(get("/test/ok").header("X-Request-Id", "bad id")).andReturn());
        String tooLong = requestIdOf(mockMvc.perform(get("/test/ok").header("X-Request-Id", "a".repeat(65))).andReturn());
        String injection = requestIdOf(mockMvc.perform(get("/test/ok").header("X-Request-Id", "x] ERROR fake [y")).andReturn());

        assertThat(withSpace).matches("[0-9a-f-]{36}");
        assertThat(tooLong).matches("[0-9a-f-]{36}");
        assertThat(injection).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("요청이 끝나면 메서드, 경로, 상태코드, 소요시간이 한 줄로 기록되고 요청 ID가 붙는다")
    void logsOneLinePerRequest(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok?q=1")).andReturn();
        String id = requestIdOf(result);

        assertThat(output.getAll().lines().filter(l -> l.contains("GET /test/ok?q=1 -> 200 ("))).hasSize(1);
        assertThat(output.getAll().lines()
                .filter(l -> l.contains("GET /test/ok?q=1 -> 200 (")).findFirst().orElseThrow())
                .contains("[" + id + "]").containsPattern("\\(\\d+ms\\)");
    }

    @Test
    @DisplayName("오류 응답 본문의 request_id는 응답 헤더 및 로그와 같다")
    void errorBodyCarriesRequestId(CapturedOutput output) throws Exception {
        MvcResult notFound = mockMvc.perform(get("/test/missing").header("X-Request-Id", "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.request_id").value("trace-404"))
                .andReturn();
        assertThat(requestIdOf(notFound)).isEqualTo("trace-404");

        mockMvc.perform(get("/test/boom").header("X-Request-Id", "trace-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.request_id").value("trace-500"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        // 500의 원인(스택 트레이스) 로그에도 같은 요청 ID가 붙어 있어서 문의 시 로그에서 찾을 수 있다
        assertThat(output.getAll().lines()
                .filter(l -> l.contains("처리되지 않은 내부 서버 오류 발생")).findFirst().orElseThrow())
                .contains("[trace-500]");
        assertThat(output.getAll()).contains("GET /test/boom -> 500 (");
    }

    @Test
    @DisplayName("/actuator 경로는 요청 로그에서 제외된다")
    void actuatorIsNotLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(output.getAll()).doesNotContain("GET /actuator/health");
    }

    @Test
    @DisplayName("요청이 끝나면 MDC가 비워진다")
    void clearsMdc() throws Exception {
        mockMvc.perform(get("/test/ok"));

        assertThat(MDC.get("requestId")).isNull();
    }
}
