package foodapi.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FoodRequest(
        @NotBlank
        @Size(max = 20)
        @Schema(description = "식품코드 (필수, 유일)", example = "D999999")
        String foodCd,

        @Size(max = 50)
        @Schema(description = "식품군", example = "찌개류")
        String groupName,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "식품명 (필수)", example = "테스트 김치찌개")
        String foodName,

        @Min(1900)
        @Max(2100)
        @Schema(description = "조사년도", example = "2024")
        Short researchYear,

        @Size(max = 50)
        @Schema(description = "지역/제조사", example = "충주")
        String makerName,

        @Size(max = 100)
        @Schema(description = "자료출처", example = "테스트 출처")
        String refName,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "1회 제공량", example = "300")
        BigDecimal servingSize,

        @Size(max = 10)
        @Schema(description = "1회 제공량 단위(g, mL)", example = "g")
        String servingUnit,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "열량(kcal)", example = "150.5")
        BigDecimal calorie,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "탄수화물(g)", example = "12.3")
        BigDecimal carbohydrate,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "단백질(g)", example = "8.1")
        BigDecimal protein,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "지방(g)", example = "6.2")
        BigDecimal fat,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "총당류(g)", example = "2.5")
        BigDecimal sugars,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "나트륨(mg)", example = "900")
        BigDecimal sodium,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "콜레스테롤(mg)", example = "20")
        BigDecimal cholesterol,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "포화지방산(g)", example = "1.5")
        BigDecimal saturatedFattyAcids,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "트랜스지방(g)", example = "0.1")
        BigDecimal transFat
) {

    public Food toEntity() {
        return toEntity(null);
    }

    public Food toEntity(Long id) {
        String cleanFoodCd = strip(foodCd);
        String cleanFoodName = strip(foodName);
        String cleanMakerName = strip(makerName);

        return Food.builder()
                .id(id)
                .foodCd(cleanFoodCd)
                .groupName(strip(groupName))
                .foodName(cleanFoodName)
                .researchYear(researchYear)
                .makerName(cleanMakerName)
                .refName(strip(refName))
                .servingSize(servingSize)
                .servingUnit(strip(servingUnit))
                .calorie(calorie)
                .carbohydrate(carbohydrate)
                .protein(protein)
                .fat(fat)
                .sugars(sugars)
                .sodium(sodium)
                .cholesterol(cholesterol)
                .saturatedFattyAcids(saturatedFattyAcids)
                .transFat(transFat)
                .foodNameSearch(SearchNormalizer.normalize(cleanFoodName))
                .makerNameSearch(SearchNormalizer.normalize(cleanMakerName))
                .build();
    }

    private static String strip(String str) {
        if (str == null) {
            return null;
        }
        String stripped = str.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}