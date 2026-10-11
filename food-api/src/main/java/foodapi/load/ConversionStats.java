package foodapi.load;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ConversionStats {
    // 행 단위 건너뜀 건수
    private int skippedCount = 0;

    // 사유별 컬럼 집계 ("컬럼명:사유" -> 횟수)
    private final Map<String, Integer> dashCountByColumn = new LinkedHashMap<>();
    private final Map<String, Integer> underLimitCountByColumn = new LinkedHashMap<>();
    private final Map<String, Integer> unparseableCountByColumn = new LinkedHashMap<>();

    public void incrementSkipped() {
        this.skippedCount++;
    }

    public void recordDash(String column) {
        dashCountByColumn.merge(column, 1, Integer::sum);
    }

    public void recordUnderLimit(String column) {
        underLimitCountByColumn.merge(column, 1, Integer::sum);
    }

    public void recordUnparseable(String column) {
        unparseableCountByColumn.merge(column, 1, Integer::sum);
    }

    // 전체 합계 헬퍼 메서드
    public int getTotalDashCount() {
        return dashCountByColumn.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getTotalUnderLimitCount() {
        return underLimitCountByColumn.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getTotalUnparseableCount() {
        return unparseableCountByColumn.values().stream().mapToInt(Integer::intValue).sum();
    }
}