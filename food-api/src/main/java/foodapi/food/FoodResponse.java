package foodapi.food;

import java.math.BigDecimal;

public record FoodResponse(
        Long id,
        String foodCd,
        String groupName,
        String foodName,
        Short researchYear,
        String makerName,
        String refName,
        BigDecimal servingSize,
        String servingUnit,
        BigDecimal calorie,
        BigDecimal carbohydrate,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal sugars,
        BigDecimal sodium,
        BigDecimal cholesterol,
        BigDecimal saturatedFattyAcids,
        BigDecimal transFat
) {

    public static FoodResponse from(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getFoodCd(),
                food.getGroupName(),
                food.getFoodName(),
                food.getResearchYear(),
                food.getMakerName(),
                food.getRefName(),
                food.getServingSize(),
                food.getServingUnit(),
                food.getCalorie(),
                food.getCarbohydrate(),
                food.getProtein(),
                food.getFat(),
                food.getSugars(),
                food.getSodium(),
                food.getCholesterol(),
                food.getSaturatedFattyAcids(),
                food.getTransFat()
        );
    }
}