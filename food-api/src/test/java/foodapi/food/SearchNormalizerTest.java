package foodapi.food;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchNormalizerTest {

    @Test
    @DisplayName("공백을 제거한다")
    void removesSpaces() {
        assertThat(SearchNormalizer.normalize("김치 찌개")).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("영문을 소문자로 바꾼다")
    void lowercases() {
        assertThat(SearchNormalizer.normalize("ABC")).isEqualTo("abc");
    }

    @Test
    @DisplayName("대소문자와 앞뒤 공백이 섞여 있어도 정규화한다")
    void mixedCaseAndSpaces() {
        assertThat(SearchNormalizer.normalize(" Kimchi 찌개 ")).isEqualTo("kimchi찌개");
    }

    @Test
    @DisplayName("탭과 줄바꿈도 제거한다")
    void removesTabsAndNewlines() {
        assertThat(SearchNormalizer.normalize("a\tb\nc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("전각 공백과 줄바꿈 불가 공백도 제거한다")
    void removesUnicodeSpaces() {
        assertThat(SearchNormalizer.normalize("a　b c")).isEqualTo("abc");
    }

    @Test
    @DisplayName("null이면 null을 반환한다")
    void nullReturnsNull() {
        assertThat(SearchNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열과 공백만 있는 문자열은 빈 문자열이 된다")
    void blankBecomesEmpty() {
        assertThat(SearchNormalizer.normalize("")).isEmpty();
        assertThat(SearchNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("이미 정규화된 값은 그대로다")
    void alreadyNormalizedIsUnchanged() {
        assertThat(SearchNormalizer.normalize("abc")).isEqualTo("abc");
    }
}
