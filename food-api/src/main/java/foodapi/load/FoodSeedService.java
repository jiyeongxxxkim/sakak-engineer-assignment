package foodapi.load;

import foodapi.food.Food;
import foodapi.food.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodSeedService {

    private static final int BATCH_SIZE = 500;

    private final FoodExcelReader reader = new FoodExcelReader();
    private final FoodRowConverter converter = new FoodRowConverter();
    private final FoodUpsertRepository foodUpsertRepository;
    private final FoodRepository foodRepository;

    public record SeedResult(
            int readRows,
            int skippedRows,
            long dbTotalCount,
            ConversionStats stats
    ) {}

    @Transactional
    public SeedResult load(Path file) {
        log.info("엑셀 시드 적재를 시작합니다. 파일 경로: {}", file);

        ConversionStats stats = new ConversionStats();
        List<Food> buffer = new ArrayList<>(BATCH_SIZE);
        AtomicInteger readRowCount = new AtomicInteger(0);

        reader.read(file, (rowNumber, row) -> {
            readRowCount.incrementAndGet();

            converter.convert(row, stats).ifPresentOrElse(
                    food -> {
                        buffer.add(food);
                        if (buffer.size() >= BATCH_SIZE) {
                            foodUpsertRepository.upsertAll(buffer);
                            buffer.clear();
                        }
                    },
                    () -> log.warn("행 {} 건너뜀: 식품코드 또는 식품명 누락", rowNumber)
            );
        });

        // 남아 있는 버퍼 비우기
        if (!buffer.isEmpty()) {
            foodUpsertRepository.upsertAll(buffer);
            buffer.clear();
        }

        long dbTotalCount = foodRepository.count();
        int totalRead = readRowCount.get();
        int skipped = stats.getSkippedCount();

        // 요약 로그 출력
        log.info("=== 식품 데이터 적재 완료 요약 ===");
        log.info("읽은 총 행 수      : {} 행", totalRead);
        log.info("건너뛴 행 수      : {} 행", skipped);
        log.info("'-' 결측치 처리 건수 : {} 건", stats.getTotalDashCount());
        log.info("'1g 미만' 처리 건수 : {} 건", stats.getTotalUnderLimitCount());
        log.info("해석 불가 처리 건수  : {} 건", stats.getTotalUnparseableCount());
        log.info("DB 전체 식품 건수   : {} 건", dbTotalCount);
        log.info("================================");

        return new SeedResult(totalRead, skipped, dbTotalCount, stats);
    }
}