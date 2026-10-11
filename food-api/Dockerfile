# 빌드 컨텍스트는 저장소 루트이다 (docker-compose.yml의 build.context 참고)

# ---- 1단계: 빌드 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 계층 캐시: 빌드 설정 파일만 먼저 복사해 의존성을 내려받는다
COPY gradlew settings.gradle ./
COPY gradle gradle
COPY algorithm/build.gradle algorithm/build.gradle
COPY food-api/build.gradle food-api/build.gradle
RUN ./gradlew --no-daemon :food-api:dependencies > /dev/null

# 소스를 복사해 실행 JAR를 만든다 (테스트는 Testcontainers가 필요해 이미지 빌드에서는 제외)
COPY food-api/src food-api/src
RUN ./gradlew --no-daemon :food-api:bootJar -x test

# ---- 2단계: 실행 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home app
COPY --from=build /workspace/food-api/build/libs/food-api.jar app.jar
# 적재용 원본 엑셀 (app.seed.file 기본값: data/food_nutrition.xlsx)
COPY food-api/data data
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
