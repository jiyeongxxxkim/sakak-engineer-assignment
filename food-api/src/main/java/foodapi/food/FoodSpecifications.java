package foodapi.food;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FoodSpecifications {

    private static final char ESCAPE_CHAR = '\\';

    public static Specification<Food> from(FoodSearchCondition condition) {
        if (condition == null) {
            return Specification.where(null);
        }

        return Specification.where(foodNameContains(condition.foodName()))
                .and(makerNameContains(condition.makerName()))
                .and(researchYearEquals(condition.researchYear()))
                .and(foodCodeEquals(condition.foodCode()));
    }

    public static Specification<Food> foodNameContains(String foodName) {
        String normalized = SearchNormalizer.normalize(foodName);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }

        String pattern = "%" + escapeLikeWildcards(normalized) + "%";
        return (root, query, cb) -> cb.like(root.get("foodNameSearch"), pattern, ESCAPE_CHAR);
    }

    public static Specification<Food> makerNameContains(String makerName) {
        String normalized = SearchNormalizer.normalize(makerName);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }

        String pattern = "%" + escapeLikeWildcards(normalized) + "%";
        return (root, query, cb) -> cb.like(root.get("makerNameSearch"), pattern, ESCAPE_CHAR);
    }

    public static Specification<Food> researchYearEquals(Short researchYear) {
        if (researchYear == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("researchYear"), researchYear);
    }

    public static Specification<Food> foodCodeEquals(String foodCode) {
        String normalized = SearchNormalizer.normalize(foodCode);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        // MySQL 콜레이션(ci) 기준 대소문자 무시 비교
        return (root, query, cb) -> cb.equal(root.get("foodCd"), normalized);
    }

    // 이스케이프 처리: %, _ 및 \ 문자를 이스케이프
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}