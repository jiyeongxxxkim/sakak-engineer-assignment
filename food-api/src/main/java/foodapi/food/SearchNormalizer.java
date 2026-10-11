package foodapi.food;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchNormalizer {

    // 공백 매칭패턴
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\u00A0\\u3000]+");

    //모든 공백 제거 후 소문자 변환
    // null 입력 시 null 반환
    //@param input 원본 문자열
    //@return 정규화된 문자열 (공백만 있던 경우 "")

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String lowerCase = input.toLowerCase(Locale.ROOT);
        return WHITESPACE_PATTERN.matcher(lowerCase).replaceAll("");
    }
}