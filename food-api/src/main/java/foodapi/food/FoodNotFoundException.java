package foodapi.food;

public class FoodNotFoundException extends RuntimeException{
    public FoodNotFoundException(Long id){
        super("식품을 찾을 수 없습니다. id=" + id);
    }
}