package foodapi.load;

import com.github.pjfanning.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class FoodExcelReader {

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 엑셀 파일을 스트리밍 방식으로 읽어 각 행 데이터를 handler에 전달합니다.
     *
     * @param path    읽을 엑셀 파일 경로
     * @param handler (엑셀 행 번호, 헤더명-셀값 맵)을 전달받는 콜백
     */
    public void read(Path path, BiConsumer<Integer, Map<String, String>> handler) {
        try (InputStream is = Files.newInputStream(path);
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .open(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                throw new IllegalStateException("엑셀 파일이 비어 있습니다: " + path.getFileName());
            }

            // 1. 헤더 행 읽기
            Row headerRow = rowIterator.next();
            Map<Integer, String> columnIndexToHeader = new HashMap<>();

            for (Cell cell : headerRow) {
                String headerName = dataFormatter.formatCellValue(cell);
                if (headerName != null) {
                    headerName = headerName.trim();
                    if (!headerName.isEmpty()) {
                        columnIndexToHeader.put(cell.getColumnIndex(), headerName);
                    }
                }
            }

            // 2. 필수 헤더 검증
            validateHeaders(columnIndexToHeader);

            // 3. 데이터 행 처리
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                int rowNumber = row.getRowNum() + 1;

                Map<String, String> rowData = new HashMap<>();

                for (Cell cell : row) {
                    String header = columnIndexToHeader.get(cell.getColumnIndex());
                    if (header == null) continue;


                    String value = dataFormatter.formatCellValue(cell);
                    if (value != null) {
                        value = value.trim();
                        // 빈 문자열은 null로 처리
                        if (!value.isEmpty()) {
                            rowData.put(header, value);
                        }
                    }
                }
                // 행 데이터가 비어있지 않은 경우만 호출
                if (!rowData.isEmpty()) {
                    handler.accept(rowNumber, rowData);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("엑셀 파일을 읽는 중 I/O 오류가 발생했습니다: " + path.getFileName(), e);
        }
    }

    // 필수 헤더 검증
    private void validateHeaders(Map<Integer, String> columnIndexToHeader) {
        Set<String> actualHeaders = new HashSet<>(columnIndexToHeader.values());
        List<String> missingHeaders = FoodRowConverter.REQUIRED_HEADERS.stream()
                .filter(required -> !actualHeaders.contains(required))
                .toList();

        if (!missingHeaders.isEmpty()) {
            throw new IllegalStateException("필수 엑셀 헤더가 누락되었습니다: " + missingHeaders);
        }
    }
}