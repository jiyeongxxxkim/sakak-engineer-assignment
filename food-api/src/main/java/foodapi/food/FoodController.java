package foodapi.food;

import foodapi.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @PostMapping
    public ResponseEntity<FoodResponse> create(@Valid @RequestBody FoodRequest request) {
        FoodResponse response = foodService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoodResponse> get(@PathVariable Long id) {
        FoodResponse response = foodService.get(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<FoodResponse>> search(
            @RequestParam(name = "food_name", required = false) String foodName,
            @RequestParam(name = "research_year", required = false) Short researchYear,
            @RequestParam(name = "maker_name", required = false) String makerName,
            @RequestParam(name = "food_code", required = false) String foodCode,
            Pageable pageable
    ) {
        FoodSearchCondition condition = new FoodSearchCondition(
                foodName,
                researchYear,
                makerName,
                foodCode
        );

        PageResponse<FoodResponse> response = foodService.search(condition, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FoodResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FoodRequest request
    ) {
        FoodResponse response = foodService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        foodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}