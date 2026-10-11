package foodapi.load;

import foodapi.food.Food;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.sql.Types;

@Repository
@RequiredArgsConstructor
public class FoodUpsertRepository {

    private final JdbcTemplate jdbcTemplate;

    // id는 AUTO_INCREMENT이므로 INSERT 시 생략하고, ON DUPLICATE KEY UPDATE 시에도 id는 업데이트하지 않음
    private static final String UPSERT_SQL = """
        INSERT INTO food (
            food_cd, group_name, food_name, research_year,
            maker_name, ref_name, serving_size, serving_unit,
            calorie, carbohydrate, protein, fat,
            sugars, sodium, cholesterol,  saturated_fatty_acids,
            trans_fat, food_name_search, maker_name_search
        ) VALUES (
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?
        ) AS new ON DUPLICATE KEY UPDATE
            group_name = new.group_name,
            food_name = new.food_name,
            research_year = new.research_year,
            maker_name = new.maker_name,
            ref_name = new.ref_name,
            serving_size = new.serving_size,
            serving_unit = new.serving_unit,
            calorie = new.calorie,
            carbohydrate = new.carbohydrate,
            protein = new.protein,
            fat = new.fat,
            sugars = new.sugars,
            sodium = new.sodium,
            cholesterol = new.cholesterol,
            saturated_fatty_acids = new.saturated_fatty_acids,
            trans_fat = new.trans_fat,
            food_name_search = new.food_name_search,
            maker_name_search = new.maker_name_search
        """;

    public void upsertAll(List<Food> foods) {
        if (foods == null || foods.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                foods,
                foods.size(),
                (ps, food) -> {
                    ps.setString(1, food.getFoodCd());
                    ps.setString(2, food.getGroupName());
                    ps.setString(3, food.getFoodName());
                    ps.setObject(4, food.getResearchYear(), Types.SMALLINT);

                    ps.setString(5, food.getMakerName());
                    ps.setString(6, food.getRefName());
                    ps.setObject(7, food.getServingSize(), Types.DECIMAL);
                    ps.setString(8, food.getServingUnit());

                    ps.setObject(9, food.getCalorie(), Types.DECIMAL);
                    ps.setObject(10, food.getCarbohydrate(), Types.DECIMAL);
                    ps.setObject(11, food.getProtein(), Types.DECIMAL);
                    ps.setObject(12, food.getFat(), Types.DECIMAL);

                    // Entity 필드명에 맞게 호출 (sugars / saturatedFattyAcids)
                    ps.setObject(13, food.getSugars(), Types.DECIMAL);
                    ps.setObject(14, food.getSodium(), Types.DECIMAL);
                    ps.setObject(15, food.getCholesterol(), Types.DECIMAL);
                    ps.setObject(16, food.getSaturatedFattyAcids(), Types.DECIMAL);

                    ps.setObject(17, food.getTransFat(), Types.DECIMAL);
                    ps.setString(18, food.getFoodNameSearch());
                    ps.setString(19, food.getMakerNameSearch());
                }
        );
    }
}