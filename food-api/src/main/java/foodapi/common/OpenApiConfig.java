package foodapi.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI foodApiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("식품영양성분 API")
                .version("v1")
                .description("식품영양성분 DB(7,683건)를 검색하고 관리하는 REST API"));
    }
}
