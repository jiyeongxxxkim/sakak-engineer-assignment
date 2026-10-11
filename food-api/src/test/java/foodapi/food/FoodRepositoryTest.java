package foodapi.food;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FoodRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private FoodRepository foodRepository;

    @Test
    @DisplayName("1. 저장 후 findById로 조회했을 때 저장한 값과 일치하는지 확인 (일부 영양소는 null)")
    void saveAndFindById() {
        // given: 일부 영양소(fat, sugar 등)는 null 상태인 Food 객체 생성
        Food food = Food.builder()
                .foodCd("FOOD_001")
                .foodName("닭가슴살 샐러드")
                .foodNameSearch("닭가슴살샐러드")
                .groupName("즉석섭취식품")
                .researchYear((short) 2024)
                .makerName("샐러드랩")
                .servingSize(new BigDecimal("200.00"))
                .servingUnit("g")
                .calorie(new BigDecimal("150.50"))
                .carbohydrate(new BigDecimal("5.20"))
                .protein(new BigDecimal("28.40"))
                .build();

        // when: 저장 후 ID로 단건 조회
        Food savedFood = foodRepository.save(food);
        Optional<Food> foundOptional = foodRepository.findById(savedFood.getId());

        // then: 데이터 일치 여부 및 null 필드 유지 확인
        assertThat(foundOptional).isPresent();
        Food foundFood = foundOptional.get();

        assertThat(foundFood.getFoodCd()).isEqualTo("FOOD_001");
        assertThat(foundFood.getFoodName()).isEqualTo("닭가슴살 샐러드");
        assertThat(foundFood.getResearchYear()).isEqualTo((short) 2024);
        assertThat(foundFood.getServingSize()).isEqualByComparingTo("200.00");
        assertThat(foundFood.getCalorie()).isEqualByComparingTo("150.50");
        assertThat(foundFood.getCarbohydrate()).isEqualByComparingTo("5.20");
        assertThat(foundFood.getProtein()).isEqualByComparingTo("28.40");

        // 명시적으로 세팅하지 않은 필드가 정상적으로 null인지 검증
        assertThat(foundFood.getFat()).isNull();
        assertThat(foundFood.getSugars()).isNull();
        assertThat(foundFood.getSodium()).isNull();
    }

    @Test
    @DisplayName("2. 동일한 food_cd를 중복 저장하면 유니크 제약조건 위반 예외가 발생하는지 확인")
    void duplicateFoodCdThrowsException() {
        // given: 동일한 foodCd를 가진 두 엔티티 준비
        Food food1 = Food.builder()
                .foodCd("DUP_CODE_999")
                .foodName("아메리카노")
                .foodNameSearch("아메리카노")
                .build();

        Food food2 = Food.builder()
                .foodCd("DUP_CODE_999") // 중복 코드
                .foodName("카페라떼")
                .foodNameSearch("카페라떼")
                .build();

        // when: 첫 번째 식품 저장 후 즉시 DB에 플러시
        foodRepository.saveAndFlush(food1);

        // then: 동일한 food_cd로 두 번째 엔티티 저장 시 유니크 제약조건 위반 예외 발생 검증
        assertThrows(DataIntegrityViolationException.class, () -> {
            foodRepository.saveAndFlush(food2);
        });
    }
}
