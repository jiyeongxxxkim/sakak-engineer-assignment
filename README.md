# Software Engineer (Backend) 기술 과제

[![CI](https://github.com/jiyeongxxxkim/sakak-engineer-assignment/actions/workflows/ci.yml/badge.svg)](https://github.com/jiyeongxxxkim/sakak-engineer-assignment/actions/workflows/ci.yml)

과제는 두 가지이며, 각각 별도 모듈과 README로 구성했습니다.

| 과제 | 위치 | 설명 |
|---|---|---|
| 1. 알고리즘: 개미수열 | [`algorithm/`](algorithm/README.md) | n번째 항(3 < n < 100)의 가운데 두 자리 수를 구하는 함수. TDD로 진행 |
| 2. 구현: 식품영양성분 API | [`food-api/`](food-api/README.md) | 식품영양성분 엑셀 적재, 식품 CRUD·검색 REST API |

## 저장소 구조
```
.
├── algorithm/   # 과제 1: 개미수열 (Java, JUnit 5)
├── food-api/    # 과제 2: 식품영양성분 API (Spring Boot, MySQL)
├── .github/workflows/ci.yml   # 푸시마다 두 모듈의 테스트와 Docker 이미지 빌드
├── gradlew, settings.gradle   # Gradle 멀티모듈 (Wrapper 포함, 별도 설치 불필요)
```

## 빠른 실행

**식품영양성분 API** (Docker만 있으면 됩니다)
```bash
cd food-api
docker compose up --build
```
- 앱과 MySQL이 함께 뜨고, 앱이 시작되면서 엑셀 7,683건이 자동 적재됩니다.
- 준비가 끝나면 http://localhost:8080/swagger-ui.html 에서 API를 바로 호출해 볼 수 있습니다.
- 포트가 이미 사용 중이면 `APP_PORT=18080 DB_PORT=3307 docker compose up --build`처럼 바꿀 수 있습니다.

**전체 테스트** (JDK 21 이상, Docker 필요 — 통합 테스트가 Testcontainers로 MySQL을 띄웁니다)
```bash
./gradlew test
```

자세한 실행 방법, 설계 의도, 한계와 보완 계획은 각 모듈의 README를 참고해 주세요.
