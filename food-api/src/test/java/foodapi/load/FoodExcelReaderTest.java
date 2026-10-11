package foodapi.load;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodExcelReaderTest {

    private static final Path REAL_FILE = Path.of("data/food_nutrition.xlsx");

    @TempDir
    Path tempDir;

    private final FoodExcelReader reader = new FoodExcelReader();

    // 테스트용 엑셀 생성: 첫 행은 헤더, 이후 행은 rowWriter가 채운다
    private Path workbook(List<String> headers, Consumer<Sheet> rowWriter) throws Exception {
        Path file = tempDir.resolve("sample.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet("Sheet0");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            rowWriter.accept(sheet);
            wb.write(out);
        }
        return file;
    }

    private List<String> requiredHeaders() {
        return new ArrayList<>(FoodRowConverter.REQUIRED_HEADERS);
    }

    private record Read(int rowNumber, Map<String, String> data) {}

    private List<Read> readAll(Path file) {
        List<Read> result = new ArrayList<>();
        reader.read(file, (n, row) -> result.add(new Read(n, row)));
        return result;
    }

    @Test
    @DisplayName("헤더 이름으로 값을 매핑하고 열 순서와 무관하다")
    void mapsByHeaderName() throws Exception {
        List<String> headers = requiredHeaders();
        java.util.Collections.reverse(headers); // 열 순서를 뒤집어도 이름으로 찾는다
        Path file = workbook(headers, sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(headers.indexOf("식품코드")).setCellValue("D000006");
            row.createCell(headers.indexOf("식품명")).setCellValue("꿩불고기");
        });

        List<Read> rows = readAll(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).data()).containsEntry("식품코드", "D000006").containsEntry("식품명", "꿩불고기");
    }

    @Test
    @DisplayName("엑셀 행 번호는 1부터 시작하며 첫 데이터 행은 2번이다")
    void passesExcelRowNumber() throws Exception {
        Path file = workbook(requiredHeaders(), sheet -> {
            sheet.createRow(1).createCell(0).setCellValue("a");
            sheet.createRow(2).createCell(0).setCellValue("b");
        });

        List<Read> rows = readAll(file);

        assertThat(rows).extracting(Read::rowNumber).containsExactly(2, 3);
    }

    @Test
    @DisplayName("완전히 빈 행은 건너뛰고 행 번호는 유지된다")
    void skipsBlankRows() throws Exception {
        Path file = workbook(requiredHeaders(), sheet -> {
            sheet.createRow(1).createCell(0).setCellValue("a");
            sheet.createRow(2).createCell(0).setCellValue("   "); // 공백만 있는 행
            sheet.createRow(4).createCell(0).setCellValue("d");   // 3번 행은 아예 없음
        });

        List<Read> rows = readAll(file);

        assertThat(rows).extracting(Read::rowNumber).containsExactly(2, 5);
    }

    @Test
    @DisplayName("빈 셀은 맵에 키를 만들지 않고 값은 trim된다")
    void blankCellsHaveNoKeyAndValuesAreTrimmed() throws Exception {
        Path file = workbook(requiredHeaders(), sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("  D000006  ");
            row.createCell(1).setCellValue("");
        });

        Map<String, String> data = readAll(file).get(0).data();

        assertThat(data).containsEntry(requiredHeaders().get(0), "D000006");
        assertThat(data).doesNotContainKey(requiredHeaders().get(1));
    }

    @Test
    @DisplayName("숫자 셀도 문자열로 읽는다")
    void readsNumericCellsAsString() throws Exception {
        Path file = workbook(requiredHeaders(), sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(368.8);
            row.createCell(1).setCellValue(500);
        });

        Map<String, String> data = readAll(file).get(0).data();

        assertThat(data).containsEntry(requiredHeaders().get(0), "368.8");
        assertThat(data).containsEntry(requiredHeaders().get(1), "500");
    }

    @Test
    @DisplayName("헤더 셀이 없는 열의 값은 무시한다")
    void ignoresColumnsWithoutHeader() throws Exception {
        List<String> headers = requiredHeaders();
        Path file = workbook(headers, sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("a");
            row.createCell(headers.size() + 1).setCellValue("x"); // 헤더가 없는 열
        });

        Map<String, String> data = readAll(file).get(0).data();

        assertThat(data).hasSize(1).containsEntry(headers.get(0), "a");
    }

    @Test
    @DisplayName("필수 헤더가 없으면 누락된 헤더 이름과 함께 즉시 실패한다")
    void failsFastWhenHeaderMissing() throws Exception {
        List<String> headers = requiredHeaders();
        headers.remove("총당류(g)");
        Path file = workbook(headers, sheet -> sheet.createRow(1).createCell(0).setCellValue("a"));

        assertThatThrownBy(() -> readAll(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("총당류(g)");
    }

    @Test
    @DisplayName("원본 엑셀을 읽으면 7,683건이고 첫 행 값이 원본과 같다")
    void readsRealFile() {
        List<Read> rows = readAll(REAL_FILE);

        assertThat(rows).hasSize(7683);
        Read first = rows.get(0);
        assertThat(first.rowNumber()).isEqualTo(2);
        assertThat(first.data())
                .containsEntry("식품코드", "D000006")
                .containsEntry("식품명", "꿩불고기")
                .containsEntry("에너지(㎉)", "368.8")
                .containsEntry("지역 / 제조사", "충주");
    }
}
