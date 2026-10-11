package foodapi.load;

import foodapi.food.Food;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 헤더 문자열은 원본 엑셀 파일의 실제 헤더를 그대로 사용한다.
class FoodRowConverterTest {

    private FoodRowConverter converter;
    private ConversionStats stats;

    @BeforeEach
    void setUp() {
        converter = new FoodRowConverter();
        stats = new ConversionStats();
    }

    private Map<String, String> validRow() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("식품코드", "D000006");
        row.put("식품대분류", "구이류");
        row.put("식품명", "꿩 불고기");
        row.put("연도", "2019");
        row.put("지역 / 제조사", "충주 Farm");
        row.put("성분표출처", "외식영양성분자료집 통합본(2012-2017년)");
        row.put("1회제공량", "500");
        row.put("내용량_단위", "g");
        row.put("에너지(㎉)", "368.8");
        row.put("탄수화물(g)", "39.7");
        row.put("단백질(g)", "33.5");
        row.put("지방(g)", "8.5");
        row.put("총당류(g)", "16.9");
        row.put("나트륨(㎎)", "1264.31");
        row.put("콜레스테롤(㎎)", "106.18");
        row.put("총 포화 지방산(g)", "1.9");
        row.put("트랜스 지방산(g)", "0.1");
        return row;
    }

    @Test
    @DisplayName("정상 행은 모든 필드가 매핑되고 검색용 값이 정규화된다")
    void convertsValidRow() {
        Optional<Food> result = converter.convert(validRow(), stats);

        assertThat(result).isPresent();
        Food food = result.get();
        assertThat(food.getFoodCd()).isEqualTo("D000006");
        assertThat(food.getGroupName()).isEqualTo("구이류");
        assertThat(food.getFoodName()).isEqualTo("꿩 불고기");
        assertThat(food.getResearchYear()).isEqualTo((short) 2019);
        assertThat(food.getMakerName()).isEqualTo("충주 Farm");
        assertThat(food.getRefName()).isEqualTo("외식영양성분자료집 통합본(2012-2017년)");
        assertThat(food.getServingSize()).isEqualByComparingTo("500");
        assertThat(food.getServingUnit()).isEqualTo("g");
        assertThat(food.getCalorie()).isEqualByComparingTo("368.8");
        assertThat(food.getCarbohydrate()).isEqualByComparingTo("39.7");
        assertThat(food.getProtein()).isEqualByComparingTo("33.5");
        assertThat(food.getFat()).isEqualByComparingTo("8.5");
        assertThat(food.getSugars()).isEqualByComparingTo("16.9");
        assertThat(food.getSodium()).isEqualByComparingTo("1264.31");
        assertThat(food.getCholesterol()).isEqualByComparingTo("106.18");
        assertThat(food.getSaturatedFattyAcids()).isEqualByComparingTo("1.9");
        assertThat(food.getTransFat()).isEqualByComparingTo("0.1");
        assertThat(food.getFoodNameSearch()).isEqualTo("꿩불고기");
        assertThat(food.getMakerNameSearch()).isEqualTo("충주farm");
        assertThat(stats.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("'-'는 null이 되고 컬럼별 건수가 집계된다")
    void dashBecomesNull() {
        Map<String, String> row = validRow();
        row.put("지방(g)", "-");
        row.put("콜레스테롤(㎎)", "-");

        Food food = converter.convert(row, stats).orElseThrow();

        assertThat(food.getFat()).isNull();
        assertThat(food.getCholesterol()).isNull();
        assertThat(stats.getTotalDashCount()).isEqualTo(2);
        assertThat(stats.getDashCountByColumn()).containsEntry("지방(g)", 1).containsEntry("콜레스테롤(㎎)", 1);
    }

    @Test
    @DisplayName("'1g 미만'은 null이 되고 별도로 집계된다")
    void underLimitBecomesNull() {
        Map<String, String> row = validRow();
        row.put("총당류(g)", "1g 미만");

        Food food = converter.convert(row, stats).orElseThrow();

        assertThat(food.getSugars()).isNull();
        assertThat(stats.getTotalUnderLimitCount()).isEqualTo(1);
        assertThat(stats.getUnderLimitCountByColumn()).containsEntry("총당류(g)", 1);
        assertThat(stats.getTotalDashCount()).isZero();
    }

    @Test
    @DisplayName("해석할 수 없는 숫자는 null이 되고 행은 유지된다")
    void unparseableBecomesNull() {
        Map<String, String> row = validRow();
        row.put("단백질(g)", "abc");

        Optional<Food> result = converter.convert(row, stats);

        assertThat(result).isPresent();
        assertThat(result.get().getProtein()).isNull();
        assertThat(stats.getUnparseableCountByColumn()).containsEntry("단백질(g)", 1);
        assertThat(stats.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("숫자 앞뒤 공백은 무시한다")
    void trimsNumbers() {
        Map<String, String> row = validRow();
        row.put("지방(g)", " 12.5 ");

        Food food = converter.convert(row, stats).orElseThrow();

        assertThat(food.getFat()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("연도가 해석 불가이면 행은 유지하고 연도만 null이다")
    void invalidYearBecomesNull() {
        Map<String, String> row = validRow();
        row.put("연도", "이천십구");

        Optional<Food> result = converter.convert(row, stats);

        assertThat(result).isPresent();
        assertThat(result.get().getResearchYear()).isNull();
        assertThat(stats.getUnparseableCountByColumn()).containsEntry("연도", 1);
    }

    @Test
    @DisplayName("식품코드가 비어 있으면 행을 건너뛴다")
    void skipsWhenFoodCdBlank() {
        Map<String, String> row = validRow();
        row.put("식품코드", "  ");

        assertThat(converter.convert(row, stats)).isEmpty();
        assertThat(stats.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("식품명이 '-'이면 행을 건너뛴다")
    void skipsWhenFoodNameDash() {
        Map<String, String> row = validRow();
        row.put("식품명", "-");

        assertThat(converter.convert(row, stats)).isEmpty();
        assertThat(stats.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("식품코드 키가 아예 없으면 행을 건너뛴다")
    void skipsWhenFoodCdMissing() {
        Map<String, String> row = validRow();
        row.remove("식품코드");

        assertThat(converter.convert(row, stats)).isEmpty();
        assertThat(stats.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("선택 컬럼의 키가 없으면 해당 필드는 null이다")
    void missingOptionalColumnIsNull() {
        Map<String, String> row = validRow();
        row.remove("지역 / 제조사");

        Food food = converter.convert(row, stats).orElseThrow();

        assertThat(food.getMakerName()).isNull();
        assertThat(food.getMakerNameSearch()).isNull();
    }
}
