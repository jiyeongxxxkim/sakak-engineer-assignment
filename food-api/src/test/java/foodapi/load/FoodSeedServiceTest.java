package foodapi.load;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "app.seed.enabled=false")
@Testcontainers
class FoodSeedServiceTest {

    private static final Path REAL_FILE = Path.of("data/food_nutrition.xlsx");

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private FoodSeedService seedService;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("TRUNCATE TABLE food");
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM food", Long.class);
    }

    private long countNull(String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM food WHERE " + column + " IS NULL", Long.class);
    }

    // 헤더 이름 -> 값 맵의 목록으로 작은 엑셀을 만든다
    private Path sample(String fileName, List<Map<String, String>> rows) throws Exception {
        Path file = tempDir.resolve(fileName);
        List<String> headers = FoodRowConverter.REQUIRED_HEADERS;
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet("Sheet0");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < headers.size(); c++) {
                    String value = rows.get(r).get(headers.get(c));
                    if (value != null) {
                        row.createCell(c).setCellValue(value);
                    }
                }
            }
            wb.write(out);
        }
        return file;
    }

    private Map<String, String> row(String code, String name, String calorie) {
        return Map.of("식품코드", code, "식품명", name, "에너지(㎉)", calorie, "지역 / 제조사", "충주 Farm");
    }

    @Test
    @DisplayName("원본 엑셀을 적재하면 7,683건이 저장되고 집계가 맞는다")
    void loadsRealFile() {
        FoodSeedService.SeedResult result = seedService.load(REAL_FILE);

        assertThat(result.readRows()).isEqualTo(7683);
        assertThat(result.skippedRows()).isZero();
        assertThat(result.dbTotalCount()).isEqualTo(7683);
        assertThat(count()).isEqualTo(7683);
        assertThat(result.stats().getTotalUnderLimitCount()).isEqualTo(16);
        assertThat(result.stats().getTotalUnparseableCount()).isZero();
    }

    @Test
    @DisplayName("원본 첫 행의 값이 컬럼에 맞게 저장된다")
    void storesFirstRowValues() {
        seedService.load(REAL_FILE);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM food WHERE food_cd = 'D000006'");

        assertThat(row.get("group_name")).isEqualTo("구이류");
        assertThat(row.get("food_name")).isEqualTo("꿩불고기");
        assertThat(((Number) row.get("research_year")).intValue()).isEqualTo(2019);
        assertThat(row.get("maker_name")).isEqualTo("충주");
        assertThat(row.get("ref_name")).isEqualTo("외식영양성분자료집 통합본(2012-2017년)");
        assertThat((BigDecimal) row.get("serving_size")).isEqualByComparingTo("500");
        assertThat(row.get("serving_unit")).isEqualTo("g");
        assertThat((BigDecimal) row.get("calorie")).isEqualByComparingTo("368.8");
        assertThat((BigDecimal) row.get("carbohydrate")).isEqualByComparingTo("39.7");
        assertThat((BigDecimal) row.get("protein")).isEqualByComparingTo("33.5");
        assertThat((BigDecimal) row.get("fat")).isEqualByComparingTo("8.5");
        assertThat((BigDecimal) row.get("sugars")).isEqualByComparingTo("16.9");
        assertThat((BigDecimal) row.get("sodium")).isEqualByComparingTo("1264.31");
        assertThat((BigDecimal) row.get("cholesterol")).isEqualByComparingTo("106.18");
        assertThat((BigDecimal) row.get("saturated_fatty_acids")).isEqualByComparingTo("1.9");
        assertThat((BigDecimal) row.get("trans_fat")).isEqualByComparingTo("0.1");
        assertThat(row.get("food_name_search")).isEqualTo("꿩불고기");
        assertThat(row.get("maker_name_search")).isEqualTo("충주");
    }

    @Test
    @DisplayName("결측('-')과 '1g 미만'은 컬럼별로 NULL로 저장된다")
    void storesNullsForMissingValues() {
        seedService.load(REAL_FILE);

        // 원본 '-' 건수 + '1g 미만' 건수
        assertThat(countNull("calorie")).isEqualTo(18);
        assertThat(countNull("fat")).isEqualTo(6119);
        assertThat(countNull("carbohydrate")).isEqualTo(5873 + 1);
        assertThat(countNull("protein")).isEqualTo(200 + 3);
        assertThat(countNull("sugars")).isEqualTo(307 + 12);
        assertThat(countNull("sodium")).isEqualTo(908);
        assertThat(countNull("cholesterol")).isEqualTo(6751);
        assertThat(countNull("saturated_fatty_acids")).isEqualTo(108);
        assertThat(countNull("trans_fat")).isEqualTo(6692);
    }

    @Test
    @DisplayName("같은 파일을 두 번 적재해도 건수가 늘지 않는다 (멱등성)")
    void isIdempotent() {
        seedService.load(REAL_FILE);
        long first = count();

        FoodSeedService.SeedResult second = seedService.load(REAL_FILE);

        assertThat(first).isEqualTo(7683);
        assertThat(count()).isEqualTo(7683);
        assertThat(second.dbTotalCount()).isEqualTo(7683);
    }

    @Test
    @DisplayName("같은 식품코드가 다시 들어오면 값을 덮어쓴다")
    void updatesExistingRow() throws Exception {
        seedService.load(sample("v1.xlsx", List.of(row("T001", "김치 찌개", "100"), row("T002", "된장국", "50"))));
        seedService.load(sample("v2.xlsx", List.of(row("T001", "묵은지 찌개", "120"))));

        assertThat(count()).isEqualTo(2);
        Map<String, Object> updated = jdbc.queryForMap("SELECT * FROM food WHERE food_cd = 'T001'");
        assertThat(updated.get("food_name")).isEqualTo("묵은지 찌개");
        assertThat(updated.get("food_name_search")).isEqualTo("묵은지찌개");
        assertThat((BigDecimal) updated.get("calorie")).isEqualByComparingTo("120");
        // 다른 행은 그대로
        assertThat(jdbc.queryForObject("SELECT food_name FROM food WHERE food_cd = 'T002'", String.class)).isEqualTo("된장국");
    }

    @Test
    @DisplayName("필수값이 없는 행은 저장하지 않고 건너뜀으로 집계한다")
    void skipsRowsWithoutRequiredValues() throws Exception {
        Path file = sample("skip.xlsx", List.of(
                row("T001", "김치", "10"),
                Map.of("식품명", "코드없는음식", "에너지(㎉)", "20"),
                row("T003", "밥", "30")));

        FoodSeedService.SeedResult result = seedService.load(file);

        assertThat(result.readRows()).isEqualTo(3);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(count()).isEqualTo(2);
    }
}
