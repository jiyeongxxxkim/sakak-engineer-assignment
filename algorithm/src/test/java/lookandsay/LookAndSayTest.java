package lookandsay;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LookAndSayTest {

    // L4=1211, L5=111221, L6=312211, L7=13112221, L8=1113213211
    @ParameterizedTest(name = "n={0} -> {1}")
    @CsvSource({
            "4, 21",
            "5, 12",
            "6, 22",
            "7, 12",
            "8, 21"
    })
    void 가운데_두_자리_수를_반환(int n, int expected) {
        assertEquals(expected, LookAndSay.middleTwoDigits(n));
    }
}
