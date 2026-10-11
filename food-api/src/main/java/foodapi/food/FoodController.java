package foodapi.food;

import foodapi.common.ErrorResponse;
import foodapi.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "식품", description = "식품영양성분 조회·검색·등록·수정·삭제")
@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @Operation(summary = "식품 등록",
            description = "식품을 등록한다. food_cd와 food_name은 필수이며, 응답의 Location 헤더에 생성된 리소스 주소가 담긴다. "
                    + "food_cd는 대소문자와 앞뒤 공백을 무시하고 중복을 판단한다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    @ApiResponse(responseCode = "400", description = "요청값이 올바르지 않음 (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 존재하는 식품코드 (DUPLICATE_FOOD_CODE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<FoodResponse> create(@Valid @RequestBody FoodRequest request) {
        FoodResponse response = foodService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "식품 단건 조회")
    @ApiResponse(responseCode = "200", description = "조회됨")
    @ApiResponse(responseCode = "400", description = "id가 숫자가 아님 (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "식품이 없음 (FOOD_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<FoodResponse> get(@PathVariable Long id) {
        FoodResponse response = foodService.get(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "식품 검색",
            description = "조건을 주면 AND로 결합해 검색한다. 조건이 없으면 전체 목록이다. "
                    + "food_name과 maker_name은 부분 일치이며 대소문자와 공백을 무시한다. "
                    + "food_code는 정확 일치(대소문자와 공백 무시), research_year는 정확 일치이다. "
                    + "페이지는 page(0부터)와 size로 지정하며 size의 기본값은 20, 최댓값은 100이다. "
                    + "정렬은 id 오름차순으로 고정되어 있어 sort 파라미터는 무시된다.")
    @ApiResponse(responseCode = "200", description = "검색 결과 (결과가 없으면 빈 content)")
    @ApiResponse(responseCode = "400", description = "research_year가 숫자가 아님 (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<PageResponse<FoodResponse>> search(
            @Parameter(description = "식품명 (부분 일치)", example = "김치찌개")
            @RequestParam(name = "food_name", required = false) String foodName,
            @Parameter(description = "조사년도 (정확 일치)", example = "2019")
            @RequestParam(name = "research_year", required = false) Short researchYear,
            @Parameter(description = "지역/제조사 (부분 일치)", example = "충주")
            @RequestParam(name = "maker_name", required = false) String makerName,
            @Parameter(description = "식품코드 (정확 일치)", example = "D000006")
            @RequestParam(name = "food_code", required = false) String foodCode,
            @ParameterObject Pageable pageable
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

    @Operation(summary = "식품 수정 (전체 교체)",
            description = "요청 본문으로 식품 전체를 교체한다. 본문에 없는 선택 필드는 null이 된다. "
                    + "다른 식품이 쓰는 food_cd로는 바꿀 수 없다.")
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "400", description = "요청값이 올바르지 않음 (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "식품이 없음 (FOOD_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "다른 식품이 쓰는 식품코드 (DUPLICATE_FOOD_CODE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public ResponseEntity<FoodResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FoodRequest request
    ) {
        FoodResponse response = foodService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "식품 삭제")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "400", description = "id가 숫자가 아님 (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "식품이 없음 (FOOD_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        foodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
