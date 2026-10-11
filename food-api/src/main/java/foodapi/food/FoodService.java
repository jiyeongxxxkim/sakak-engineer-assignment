package foodapi.food;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;

    @Transactional
    public FoodResponse create(FoodRequest request) {
        Food entity = request.toEntity();

        if (foodRepository.existsByFoodCd(entity.getFoodCd())) {
            throw new DuplicateFoodCodeException(entity.getFoodCd());
        }

        Food saved = foodRepository.save(entity);
        return FoodResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public FoodResponse get(Long id) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new FoodNotFoundException(id));
        return FoodResponse.from(food);
    }

    @Transactional
    public FoodResponse update(Long id, FoodRequest request) {
        if (!foodRepository.existsById(id)) {
            throw new FoodNotFoundException(id);
        }

        Food entity = request.toEntity(id);
        if (foodRepository.existsByFoodCdAndIdNot(entity.getFoodCd(), id)) {
            throw new DuplicateFoodCodeException(entity.getFoodCd());
        }

        Food updated = foodRepository.save(entity);
        return FoodResponse.from(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!foodRepository.existsById(id)) {
            throw new FoodNotFoundException(id);
        }
        foodRepository.deleteById(id);
    }
}