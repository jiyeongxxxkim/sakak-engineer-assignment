package foodapi.food;

import foodapi.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Transactional(readOnly = true)
    public PageResponse<FoodResponse> search(FoodSearchCondition condition, Pageable pageable) {
        // 클라이언트의 sort 파라미터는 무시하고 id 오름차순으로 고정
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("id").ascending()
        );

        Page<Food> foodPage = foodRepository.findAll(
                FoodSpecifications.from(condition),
                safePageable
        );

        return PageResponse.from(foodPage.map(FoodResponse::from));
    }
}