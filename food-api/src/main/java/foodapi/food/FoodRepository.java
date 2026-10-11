package foodapi.food;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {
    boolean existsByFoodCd(String foodCd);
    boolean existsByFoodCdAndIdNot(String foodCd, Long id);
}