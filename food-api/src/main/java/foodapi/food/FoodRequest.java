package foodapi.food;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodRequest(
        @NotBlank
        @Size(max = 20)
        String foodCd,

        @Size(max = 50)
        String groupName,

        @NotBlank
        @Size(max = 100)
        String foodName,

        @Min(1900)
        @Max(2100)
        Short researchYear,

        @Size(max = 50)
        String makerName,

        @Size(max = 100)
        String refName,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal servingSize,

        @Size(max = 10)
        String servingUnit,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal calorie,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal carbohydrate,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal protein,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal fat,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal sugars,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal sodium,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal cholesterol,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal saturatedFattyAcids,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
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