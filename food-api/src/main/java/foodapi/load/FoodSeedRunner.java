package foodapi.load;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class FoodSeedRunner implements ApplicationRunner {

    private final FoodSeedService foodSeedService;

    @Value("${app.seed.file}")
    private String seedFilePath;

    @Override
    public void run(ApplicationArguments args) {
        log.info("FoodSeedRunner 기동: app.seed.enabled=true");
        Path path = Path.of(seedFilePath);
        foodSeedService.load(path);
    }
}