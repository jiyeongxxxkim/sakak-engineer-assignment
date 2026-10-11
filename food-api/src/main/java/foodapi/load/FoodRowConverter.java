package foodapi.load;

import foodapi.food.Food;
import foodapi.food.SearchNormalizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FoodRowConverter {

    // 엑셀 헤더 매핑 (실제 엑셀 헤더 문자열에 맞춤)
    private static final String COL_FOOD_CD = "식품코드";
    private static final String COL_GROUP_NAME = "식품대분류";
    private static final String COL_FOOD_NAME = "식품명";
    private static final String COL_RESEARCH_YEAR = "연도";
    private static final String COL_MAKER_NAME = "지역 / 제조사";
    private static final String COL_REF_NAME = "성분표출처";
    private static final String COL_SERVING_SIZE = "1회제공량";
    private static final String COL_SERVING_UNIT = "내용량_단위";

    private static final String COL_CALORIE = "에너지(㎉)";
    private static final String COL_CARB = "탄수화물(g)";
    private static final String COL_PROTEIN = "단백질(g)";
    private static final String COL_FAT = "지방(g)";
    private static final String COL_SUGAR = "총당류(g)";
    private static final String COL_SODIUM = "나트륨(㎎)";
    private static final String COL_CHOLESTEROL = "콜레스테롤(㎎)";
    private static final String COL_SAT_FAT = "총 포화 지방산(g)";
    private static final String COL_TRANS_FAT = "트랜스 지방산(g)";

    public static final List<String> REQUIRED_HEADERS = List.of(
            COL_FOOD_CD,
            COL_GROUP_NAME,
            COL_FOOD_NAME,
            COL_RESEARCH_YEAR,
            COL_MAKER_NAME,
            COL_REF_NAME,
            COL_SERVING_SIZE,
            COL_SERVING_UNIT,
            COL_CALORIE,
            COL_CARB,
            COL_PROTEIN,
            COL_FAT,
            COL_SUGAR,
            COL_SODIUM,
            COL_CHOLESTEROL,
            COL_SAT_FAT,
            COL_TRANS_FAT
    );

    // 엑셀 행 데이터를 Food 엔티티로 변환
    public Optional<Food> convert(Map<String, String> row, ConversionStats stats) {
        String foodCd = text(row, COL_FOOD_CD);
        String foodName = text(row, COL_FOOD_NAME);

        // 1. 필수값 검사 및 건너뛰기
        // - 식품코드가 없거나 빈 문자열인 경우
        // - 식품명이 없거나, 빈 문자열이거나, "-" 인 경우
        if (foodCd == null || foodCd.isEmpty() || foodName == null || foodName.isEmpty() || "-".equals(foodName)) {
            stats.incrementSkipped();
            return Optional.empty();
        }

        String makerName = text(row, COL_MAKER_NAME);

        // 2. 값 파싱 및 엔티티 생성
        Food food = Food.builder()
                .foodCd(foodCd)
                .groupName(text(row, COL_GROUP_NAME))
                .foodName(foodName)
                .researchYear(parseShort(row, COL_RESEARCH_YEAR, stats))
                .makerName(makerName)
                .refName(text(row, COL_REF_NAME))
                .servingSize(parseDecimal(row, COL_SERVING_SIZE, stats))
                .servingUnit(text(row, COL_SERVING_UNIT))
                .calorie(parseDecimal(row, COL_CALORIE, stats))
                .carbohydrate(parseDecimal(row, COL_CARB, stats))
                .protein(parseDecimal(row, COL_PROTEIN, stats))
                .fat(parseDecimal(row, COL_FAT, stats))
                .sugars(parseDecimal(row, COL_SUGAR, stats))
                .sodium(parseDecimal(row, COL_SODIUM, stats))
                .cholesterol(parseDecimal(row, COL_CHOLESTEROL, stats))
                .saturatedFattyAcids(parseDecimal(row, COL_SAT_FAT, stats))
                .transFat(parseDecimal(row, COL_TRANS_FAT, stats))
                .foodNameSearch(SearchNormalizer.normalize(foodName))
                .makerNameSearch(SearchNormalizer.normalize(makerName))
                .build();

        return Optional.of(food);
    }

    // 문자열 값 추출 및 공백 제거
    public String text(Map<String, String> row, String column) {
        if (row == null || !row.containsKey(column)) return null;
        String val = row.get(column);
        return val == null ? null : val.trim();
    }

    // BigDecimal 파싱
    public BigDecimal parseDecimal(Map<String, String> row, String column, ConversionStats stats) {
        String val = text(row, column);
        if (val == null || val.isEmpty()) return null;


        // 규칙: "-"
        if ("-".equals(val)) {
            stats.recordDash(column);
            return null;
        }

        // 규칙: "미만" 포함
        if (val.contains("미만")) {
            stats.recordUnderLimit(column);
            return null;
        }

        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            // 규칙: 숫자 변환 불가
            stats.recordUnparseable(column);
            return null;
        }
    }

    // Short 파싱
    public Short parseShort(Map<String, String> row, String column, ConversionStats stats) {
        String val = text(row, column);
        if (val == null || val.isEmpty()) {
            return null;
        }

        if ("-".equals(val)) {
            stats.recordDash(column);
            return null;
        }

        try {
            return Short.parseShort(val);
        } catch (NumberFormatException e) {
            stats.recordUnparseable(column);
            return null;
        }
    }

}