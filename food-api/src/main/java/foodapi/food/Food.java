package foodapi.food;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "food")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String foodCd;

    private String groupName;

    private String foodName;

    private Short researchYear;

    private String makerName;

    private String refName;

    private BigDecimal servingSize;

    private String servingUnit;

    // 영양성분 9개 (소수정밀도 위해 BigDecimal 사용)
    private BigDecimal calorie;

    private BigDecimal carbohydrate;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal sugars;

    private BigDecimal sodium;

    private BigDecimal cholesterol;

    private BigDecimal saturatedFattyAcids;

    private BigDecimal transFat;

    // 검색용 필드(정규화된 이름)
    private String foodNameSearch;

    private String makerNameSearch;



}