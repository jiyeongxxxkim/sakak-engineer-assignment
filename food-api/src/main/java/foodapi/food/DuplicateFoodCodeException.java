package foodapi.food;

public class DuplicateFoodCodeException extends RuntimeException {

    public DuplicateFoodCodeException(String foodCd) {
        super("이미 존재하는 식품코드입니다. food_cd=" + foodCd);
    }
}