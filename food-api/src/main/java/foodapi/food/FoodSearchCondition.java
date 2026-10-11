package foodapi.food;

public record FoodSearchCondition(
        String foodName,
        Short researchYear,
        String makerName,
        String foodCode
) {
}