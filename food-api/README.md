# 식품영양성분 API

식품의약품안전처 식품영양성분 DB(엑셀)를 MySQL에 적재하고, 식품을 검색·조회·등록·수정·삭제하는 REST API입니다.

- Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8.4, Flyway
- 테스트: JUnit 5, MockMvc, Testcontainers(실제 MySQL)
- 배포: Dockerfile, docker-compose, GitHub Actions CI
- 문서: Swagger UI (springdoc-openapi)

## 목차
1. [실행 방법](#1-실행-방법)
2. [데이터 적재](#2-데이터-적재)
3. [API](#3-api)
4. [검색 방식](#4-검색-방식)
5. [설계 결정과 이유](#5-설계-결정과-이유)
6. [인덱스](#6-인덱스)
7. [운영 편의 (로깅, 헬스체크)](#7-운영-편의-로깅-헬스체크)
8. [한계와 보완 계획](#8-한계와-보완-계획)
9. [AI 활용 내역](#9-ai-활용-내역)
10. [소감](#10-소감)

---

## 1. 실행 방법

### Docker (권장)
```bash
cd food-api
docker compose up --build
```
- `db`(MySQL 8.4)가 healthy가 된 뒤 `app`이 시작되고, 시작할 때 `data/food_nutrition.xlsx`를 적재합니다.
- 앱은 적재가 끝난 뒤에야 준비 상태(`/actuator/health/readiness`)가 됩니다. compose 헬스체크가 이 주소를 보므로 `healthy`가 되면 데이터가 이미 들어 있습니다.
- 접속: API `http://localhost:8080/foods`, 문서 `http://localhost:8080/swagger-ui.html`
- 종료: `docker compose down` (데이터 유지), `docker compose down -v` (데이터까지 삭제)

환경변수는 `food-api/.env.example`을 `.env`로 복사해 바꿀 수 있습니다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `APP_PORT` | 8080 | 앱 포트 |
| `DB_PORT` | 3306 | MySQL 호스트 포트 (로컬 MySQL과 충돌하면 변경) |
| `SEED_ENABLED` | true (compose) / false (앱 단독) | 시작 시 엑셀 적재 여부 |
| `SEED_FILE` | `data/food_nutrition.xlsx` | 적재할 엑셀 경로 |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | food, food, food, root | DB 접속 정보 |

### 로컬에서 직접 실행 (JDK 21 이상)
```bash
cd food-api
docker compose up -d db                        # MySQL만 실행
SEED_ENABLED=true ../gradlew :food-api:bootRun # 앱 실행 + 적재
```

### 테스트
```bash
./gradlew :food-api:test     # food-api만
./gradlew test               # 전체 (알고리즘 포함)
```
- Docker가 필요합니다. 통합 테스트가 Testcontainers로 일회용 MySQL 컨테이너를 띄우므로 로컬에 DB를 설치하지 않아도 됩니다.
- 테스트 구성: 단위 테스트(정규화, 값 변환, 엑셀 리더), 적재 통합 테스트(원본 파일 적재, 멱등성, 갱신, 누락 행), API 통합 테스트(CRUD, 검색 조합, 페이지, 오류 케이스), 예외 처리·요청 추적·OpenAPI 문서 검증
- GitHub Actions가 `main` 푸시와 PR마다 `./gradlew build`(두 모듈 테스트)와 Docker 이미지 빌드를 실행합니다.

---

## 2. 데이터 적재

### 방식
앱 시작 시 `ApplicationRunner`가 엑셀을 읽어 DB에 넣습니다(`app.seed.enabled=true`일 때만 동작).

```
엑셀 → FoodExcelReader(스트리밍 읽기) → FoodRowConverter(값 변환) → FoodUpsertRepository(배치 upsert)
```

- **원본 파일**: `food-api/data/food_nutrition.xlsx` (원본 이름 `통합_식품영양성분DB_음식_20230715.xlsx`를 영문으로 바꿔 저장)
- **건수**: 과제 안내문에는 9만 105건이라고 되어 있으나 제공된 파일은 **7,683건**(100개 컬럼)입니다. 이 중 출력 항목에 필요한 컬럼만 저장합니다. 파일 이름(`..._음식_...`)과 `DB군` 컬럼이 전부 "음식"인 점으로 보아, 9만 건 통합 DB 중 음식 분류만 담은 파일로 추정합니다.
- **스키마**: Flyway 마이그레이션(`V1__create_food.sql`)이 앱 시작 시 테이블을 만듭니다.

### 재실행 시 중복 방지 (멱등성)
- `food_cd`에 UNIQUE 제약을 걸고 `INSERT ... ON DUPLICATE KEY UPDATE`(upsert)로 저장합니다. 같은 식품코드가 있으면 새로 넣지 않고 값을 갱신합니다.
- "이미 데이터가 있으면 적재하지 않는" 방식은 처음 실행이 중간에 실패해 일부만 들어갔을 때 다음 실행이 완료된 줄 알고 건너뛰는 문제가 있고, 엑셀이 바뀌어도 반영되지 않아 채택하지 않았습니다.
- 전체를 **하나의 트랜잭션**으로 묶어서, 중간에 실패하면 전부 취소됩니다.
- 확인: 적재를 두 번 실행해도 건수는 7,683건으로 같습니다(통합 테스트와 Docker 재시작으로 확인).
- 참고: upsert는 갱신하는 경우에도 자동 증가 번호를 소비해서, 여러 번 적재하면 `id`가 연속적이지 않을 수 있습니다(동작에는 영향 없음).

### 결측값과 타입 변환
원본은 모든 셀이 문자열입니다.

| 원본 값 | 처리 | 건수 |
|---|---|---|
| `-` (결측) | `NULL` | 숫자 컬럼 합계 26,976개 셀 |
| `1g 미만` | `NULL` | **총 16건** (총당류 12, 단백질 3, 탄수화물 1) |
| 숫자로 해석할 수 없는 값 | `NULL`로 두고 건수 집계 (행은 유지) | 0건 |
| 식품코드 또는 식품명이 비어 있음 | **행을 건너뜀** (경고 로그에 행 번호) | 0건 |
| 문자열 앞뒤 공백, 빈 문자열 | 공백 제거, 빈 값은 `NULL` | |
| 연도 | `Short`로 변환 (해석 불가면 `NULL`) | |
| 1회제공량 | 숫자(`serving_size`)와 단위(`serving_unit`: g 또는 mL)로 분리 | |

- **`1g 미만`은 임의의 값(0, 0.5 등)으로 바꾸지 않고 `NULL`로 두었습니다.** 실제 측정값 0이나 0.5와 구분되지 않게 하려는 선택입니다. 대신 결측(`-`)과 `1g 미만`은 둘 다 `NULL`로 저장되어 DB 안에서는 구분되지 않는데, 적재 로그에서 건수를 따로 집계해 구분해 둡니다(총 16건).
- 영양소는 소수 오차를 피하기 위해 `DECIMAL(10,2)`로 저장합니다.
- 컬럼별 `NULL` 건수(적재 후 DB 기준): 열량 18, 탄수화물 5,874, 단백질 203, 지방 6,119, 총당류 319, 나트륨 908, 콜레스테롤 6,751, 포화지방산 108, 트랜스지방 6,692

### 원본 컬럼과 API 응답 항목 매핑

| API / DB 컬럼 | 원본 엑셀 컬럼 | 비고 |
|---|---|---|
| `id` | (없음) | DB 자동 증가. 엑셀의 `NO`는 사용하지 않음 |
| `food_cd` | 식품코드 | UNIQUE |
| `group_name` | 식품대분류 | `DB군`은 전부 "음식"이라 구분력이 없고, `식품상세분류`는 너무 세분화되어 대분류를 사용 |
| `food_name` | 식품명 | |
| `research_year` | 연도 | |
| `maker_name` | 지역 / 제조사 | |
| `ref_name` | 성분표출처 | |
| `serving_size` | 1회제공량 | |
| `serving_unit` | 내용량_단위 | 과제 출력 항목에는 없으나, 단위 없는 제공량은 해석이 모호해 추가 |
| `calorie` | 에너지(㎉) | |
| `carbohydrate` | 탄수화물(g) | |
| `protein` | 단백질(g) | |
| `fat` | 지방(g) | |
| `sugars` | 총당류(g) | |
| `sodium` | 나트륨(㎎) | |
| `cholesterol` | 콜레스테롤(㎎) | |
| `saturated_fatty_acids` | 총 포화 지방산(g) | |
| `trans_fat` | 트랜스 지방산(g) | |

- 위 외의 컬럼(아미노산, 비타민 등 83개)은 저장하지 않습니다.
- 컬럼 이름은 **위치가 아니라 헤더 이름**으로 찾고, 필수 헤더가 하나라도 없으면 적재를 즉시 중단합니다(헤더가 틀려 조용히 `NULL`이 되는 사고 방지).
- 검색용 컬럼 `food_name_search`, `maker_name_search`는 소문자로 바꾸고 공백을 모두 제거한 값을 적재할 때 함께 저장합니다(검색 방식은 4장).

### 적재 후 확인
- **적재 로그 요약**: 읽은 행 수(7,683), 건너뛴 행 수(0), `-` 건수, `1g 미만` 건수(16), 해석 불가 건수, DB 전체 건수를 출력합니다.
- **조회로 확인**: `curl "localhost:8080/foods?size=1"`의 `total_elements`가 7683이어야 합니다.
- **자동화된 검증**(통합 테스트): 원본 적재 후 7,683건, 첫 행 값이 원본과 일치, 컬럼별 `NULL` 건수가 원본 집계와 일치, 재적재 시 건수 유지, 값이 바뀐 행은 갱신, 필수값 누락 행은 건너뜀.

---

## 3. API

Swagger UI(`/swagger-ui.html`)에서 모든 엔드포인트를 확인하고 직접 호출할 수 있습니다. OpenAPI 문서는 `/v3/api-docs`입니다. 응답 JSON의 키는 모두 snake_case입니다.

| 동작 | 요청 | 성공 |
|---|---|---|
| 등록 | `POST /foods` | 201 + `Location` 헤더 |
| 단건 조회 | `GET /foods/{id}` | 200 |
| 검색(목록) | `GET /foods?...` | 200 (페이지 응답) |
| 수정(전체 교체) | `PUT /foods/{id}` | 200 |
| 삭제 | `DELETE /foods/{id}` | 204 |

### 요청/응답 예시
**등록**
```bash
curl -i -X POST localhost:8080/foods -H 'Content-Type: application/json' \
  -d '{"food_cd":"D999999","food_name":"테스트 김치찌개","maker_name":"충주","calorie":150.5}'
```
```
HTTP/1.1 201
Location: http://localhost:8080/foods/7684

{"id":7684,"food_cd":"D999999","group_name":null,"food_name":"테스트 김치찌개","research_year":null,
 "maker_name":"충주","ref_name":null,"serving_size":null,"serving_unit":null,"calorie":150.5,
 "carbohydrate":null,"protein":null,"fat":null,"sugars":null,"sodium":null,"cholesterol":null,
 "saturated_fatty_acids":null,"trans_fat":null}
```
필수는 `food_cd`와 `food_name`뿐입니다. 응답 항목은 과제의 출력 항목 17개이며, 앞에서 설명한 `serving_unit`이 하나 더 있습니다.

**단건 조회** `GET /foods/1`
```json
{"id":1,"food_cd":"D000006","group_name":"구이류","food_name":"꿩불고기","research_year":2019,
 "maker_name":"충주","ref_name":"외식영양성분자료집 통합본(2012-2017년)","serving_size":500.00,"serving_unit":"g",
 "calorie":368.80,"carbohydrate":39.70,"protein":33.50,"fat":8.50,"sugars":16.90,"sodium":1264.31,
 "cholesterol":106.18,"saturated_fatty_acids":1.90,"trans_fat":0.10}
```

**수정** `PUT /foods/{id}`는 **전체 교체**입니다. 요청에 없는 선택 필드는 `NULL`이 됩니다. 부분 수정(PATCH)은 제공하지 않습니다.

### 오류 응답
모든 오류는 같은 구조입니다. `request_id`는 서버 로그에서 해당 요청을 찾는 데 씁니다(7장).

```json
{"status":404,"code":"FOOD_NOT_FOUND","message":"식품을 찾을 수 없습니다. id=99999999","request_id":"b65e0054-fe62-4b95-a0eb-55dddbb89b09"}
```
검증 오류는 필드별 목록(`errors`)이 추가됩니다.
```json
{"status":400,"code":"INVALID_REQUEST","message":"요청 데이터 검증에 실패했습니다.","request_id":"...",
 "errors":[{"field":"food_cd","message":"공백일 수 없습니다"},{"field":"calorie","message":"다음 값 이상이어야 합니다 0"}]}
```

| 상황 | 상태 | `code` |
|---|---|---|
| 없는 식품 조회·수정·삭제 | 404 | `FOOD_NOT_FOUND` |
| 중복된 식품코드로 등록·수정 | 409 | `DUPLICATE_FOOD_CODE` |
| 잘못된 요청값(필수 누락, 범위·길이 초과, 깨진 JSON, 숫자 필드에 문자, 숫자가 아닌 id) | 400 | `INVALID_REQUEST` |
| 존재하지 않는 경로 | 404 | `NOT_FOUND` |
| 지원하지 않는 HTTP 메서드 | 405 | `METHOD_NOT_ALLOWED` |
| 지원하지 않는 Content-Type | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 예상하지 못한 서버 오류 | 500 | `INTERNAL_ERROR` |

- 500 응답에는 내부 정보(DB 주소, SQL, 스택 트레이스)를 담지 않고 고정 문구만 내리며, 원인은 서버 로그에만 남깁니다.
- 식품코드 중복은 서비스가 먼저 확인하고(409), 동시에 같은 코드가 들어와 확인을 통과해도 DB의 UNIQUE 제약이 막아 같은 409로 응답합니다.
- `food_cd`는 **대소문자와 앞뒤 공백을 무시하고** 중복을 판단합니다(`D000006`과 `d000006`은 같은 코드). DB 콜레이션(`utf8mb4_0900_ai_ci`)이 대소문자를 구분하지 않기 때문이고, 입력의 앞뒤 공백은 서버가 제거합니다.

### 요청값 검증

| 필드 | 규칙 |
|---|---|
| `food_cd` | 필수, 최대 20자 |
| `food_name` | 필수, 최대 100자 |
| `group_name`, `maker_name` | 최대 50자 |
| `ref_name` | 최대 100자 |
| `serving_unit` | 최대 10자 |
| `research_year` | 1900~2100 |
| `serving_size`와 영양소 | 0 이상, 정수 8자리·소수 2자리 이내 |

---

## 4. 검색 방식

```
GET /foods?food_name=김치&research_year=2019&maker_name=충주&food_code=D000006&page=0&size=20
```

| 파라미터 | 방식 |
|---|---|
| `food_name` | **부분 일치**, 대소문자와 공백 무시 |
| `maker_name` | **부분 일치**, 대소문자와 공백 무시 |
| `research_year` | 정확 일치 |
| `food_code` | 정확 일치, 대소문자와 공백 무시 |

- 모든 조건은 선택이며, **여러 조건은 AND**로 결합합니다. 조건이 없으면 전체 목록입니다.
- 값이 비어 있거나 공백뿐인 파라미터는 조건이 없는 것으로 봅니다.
- **정규화**: 입력과 저장값을 같은 규칙(소문자 변환 + 모든 공백 제거, `SearchNormalizer`)으로 바꿔서 비교합니다. 그래서 `김치찌개`로 검색해도 저장된 `김치 찌개`가 검색되고, `KIMCHI`로 `Kimchi 볶음밥`을 찾을 수 있습니다. 저장값은 적재·등록·수정 시 `food_name_search`, `maker_name_search` 컬럼에 함께 저장됩니다.
- **부분 일치의 와일드카드**: `LIKE`의 `%`, `_`, `\`는 이스케이프해서 입력한 글자 그대로 검색합니다(`food_name=_`이 모든 식품과 일치하는 일이 없습니다).
- `research_year`가 숫자가 아니면 400입니다.

### 페이지네이션
- `page`(0부터)와 `size`로 요청하고, 응답은 다음 구조입니다.
```json
{"content":[ {...} ],"page":0,"size":20,"total_elements":7683,"total_pages":385}
```
- `size` 기본값은 20, **최댓값은 100**입니다. 100을 넘기면 100으로, 0 이하이거나 `page`가 음수이면 기본값으로 보정합니다(오류가 아니라 보정).
- **정렬은 `id` 오름차순으로 고정**합니다. `sort` 파라미터는 무시합니다. 임의 컬럼 정렬은 인덱스가 없어 느리고 존재하지 않는 컬럼명이면 오류가 나기 때문입니다.
- 범위를 벗어난 `page`나 결과가 없는 검색은 200과 빈 `content`를 돌려줍니다.

---

## 5. 설계 결정과 이유

| 결정 | 이유 |
|---|---|
| **JPA + JdbcTemplate 병행** | CRUD와 검색은 JPA(Specification)로, 대량 upsert는 MySQL 문법(`ON DUPLICATE KEY UPDATE`)이 필요해 JdbcTemplate으로 처리 |
| **검색 조건은 Specification** | 선택 조건 4개로 만들어지는 16가지 조합을 조건 조각으로 이어 붙임 |
| **PUT(전체 교체)만 제공** | 필드가 17개로 적고, 부분 수정이 일으킬 수 있는 데이터 불일치를 피함. PATCH는 보완 계획 |
| **엑셀 스트리밍 읽기** | 일반 방식(POI `XSSFWorkbook`)은 2.7MB 파일을 읽는 데 약 730MB 힙이 필요했고 테스트 기본 힙에서 `OutOfMemoryError`가 남. 한 행씩 읽는 `excel-streaming-reader`로 바꿔 메모리가 파일 크기에 비례하지 않음 |
| **upsert + 단일 트랜잭션** | 재실행해도 결과가 같고(멱등), 실패하면 전부 취소 |
| **`rewriteBatchedStatements=true`** | 배치 INSERT를 다중 행 INSERT로 묶어 DB 왕복을 줄임. 로컬 측정에서 적재 시간 약 6.7초 → 5.4초(약 20% 개선). DB가 같은 컴퓨터에 있어 네트워크 비용이 거의 없어서 차이가 작고, 원격 DB에서는 더 클 것으로 예상 |
| **요청·응답 DTO 분리** | 검증 규칙을 요청 DTO에 두고, 검색용 컬럼 같은 내부 값은 응답에 노출하지 않음. 서비스는 트랜잭션 안에서 DTO로 변환해 반환 |
| **전역 예외 처리 한 곳** | 오류 응답 형식을 하나로 통일. Spring이 던지는 404·405·415도 직접 처리(마지막 `Exception` 핸들러가 이를 500으로 바꾸던 문제를 테스트로 발견해 수정) |
| **Flyway 마이그레이션** | 스키마를 SQL 파일로 버전 관리해 `git clone` 후 같은 테이블이 만들어지게 함. JPA는 `ddl-auto=validate`로 엔티티와 스키마 불일치만 검증 |
| **Testcontainers** | 로컬 DB 설치 없이 실제 MySQL로 통합 테스트(MySQL 고유 문법인 upsert도 검증 가능) |
| **readiness 프로브를 헬스체크로 사용** | 적재(`ApplicationRunner`)는 웹 서버가 켜진 뒤 실행되어, 일반 헬스체크는 적재 중에도 `UP`이 되어 빈 결과를 볼 수 있었음. readiness는 적재가 끝난 뒤에 `UP`이 됨 |

---

## 6. 인덱스

현재 인덱스: `PRIMARY(id)`, `uk_food_cd`(UNIQUE), `idx_food_research_year`.

- `uk_food_cd`는 중복 방지(upsert, 409 처리)에 필요한 제약입니다.
- `idx_food_research_year`는 설계 초안 단계에서 만든 인덱스입니다. `research_year`는 값이 4종류뿐이고 2019년이 전체의 약 88%라서 효과가 값에 따라 다릅니다(희소한 연도에서는 크고 2019년 검색에서는 제한적). **인덱스가 없을 때와의 비교 측정은 하지 않았습니다.**

처음에는 9만 건 규모를 고려해 인덱스를 더 도입하려 했으나, AI(Claude Code)를 활용해 9만 건 규모로 측정해 본 결과 추가는 과하다고 판단해 추가하지 않았습니다.

- **측정 방법**: 7,683건을 12배로 복제한 92,196건 임시 테이블에서 `EXPLAIN ANALYZE`로 측정(로컬 MySQL 8.4, 측정 후 테이블 삭제). 합성 데이터라 실제 9만 건의 분포와 다를 수 있습니다.

| 쿼리 | 접근 방식 | 시간 |
|---|---|---|
| `food_cd` 정확 일치 (대소문자 달라도) | UNIQUE 인덱스 조회 | 약 0.0002ms |
| `research_year=2018` 페이지 / 개수 | 인덱스 | 0.08ms / 0.09ms |
| `research_year=2019`(전체의 88%) 페이지 / 개수 | 인덱스 / 커버링 인덱스 | 1.1ms / 7.1ms |
| 식품명 `LIKE '%김치찌개%'` 페이지 / 개수 | 전체 스캔 | 1.4ms / 17ms |
| 제조사 `LIKE '%충주%'` 페이지 | 전체 스캔 | 24.7ms |

- 기존 인덱스는 모두 쓰이고, `research_year` 인덱스는 정렬 키 `id`를 함께 가져 `ORDER BY id LIMIT`에 별도 정렬이 필요 없습니다.
- 인덱스를 못 타는 부분 일치(`LIKE '%x%'`)도 가장 느린 경우가 약 25ms였습니다.
- **검토한 대안**: 식품명에 FULLTEXT(ngram) 인덱스를 걸면 개수 쿼리가 17ms → 0.4ms로 줄지만, **한 글자 검색**(`김`)이 `LIKE`는 1,176건인데 FULLTEXT는 0건이 되어 검색 결과가 달라지고 쿼리 수정이 필요해 보류했습니다.

---

## 7. 운영 편의 (로깅, 헬스체크)

- **요청 추적**: 요청마다 ID를 정해 응답 헤더 `X-Request-Id`와 로그에 남기고, 오류 응답 본문의 `request_id`에도 담습니다. 클라이언트가 `X-Request-Id`를 보내면 그 값을 쓰되, 영숫자와 `. _ -`(최대 64자)만 허용합니다(로그 위조 방지).
- **요청 로그**: 요청이 끝나면 한 줄로 기록합니다. 헬스체크·Swagger 경로는 제외합니다.
```
INFO [my-trace-404] ...RequestLoggingFilter : GET /foods/99999999 -> 404 (5ms)
```
- **헬스체크(Actuator)**: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`

---

## 8. 한계와 보완 계획

- **부분 일치 검색은 인덱스를 타지 못합니다.** 현재 규모에서는 문제가 없으나 데이터가 훨씬 커지면 FULLTEXT(ngram)를 검토하되, 한 글자 검색 처리가 필요합니다.
- **offset 페이지네이션**은 뒤쪽 페이지로 갈수록 느려지고, 매 요청마다 전체 건수를 세는 쿼리가 함께 실행됩니다. 커지면 cursor(keyset) 방식이나 건수 생략(Slice)을 고려합니다.
- **적재는 단일 트랜잭션**이라 데이터가 매우 크면 트랜잭션이 길어집니다. 큰 데이터에서는 일정 건수마다 커밋하는 방식(upsert라 재실행으로 복구 가능)을 고려합니다. 엑셀 읽기는 스트리밍으로 구현되어있습니다.
- **적재 동시 실행**: 앱을 여러 개 띄우면 동시에 적재를 시작할 수 있습니다(결과는 upsert라 안전하지만 낭비로 판단).
- **엑셀에서 지워진 행은 DB에서 지워지지 않습니다.** 적재는 추가와 갱신(upsert)만 하므로 원본과 DB를 완전히 동기화하지는 않습니다. 필요하면 적재 후 엑셀에 없는 `food_cd`를 정리하는 단계를 추가할 수 있습니다.
- **등록 응답과 조회 응답의 소수 자릿수가 다릅니다**(`150.5` vs `150.50`). 등록 응답은 요청값을 그대로, 조회 응답은 DB의 `DECIMAL(10,2)` 값을 돌려주기 때문이며 값은 같습니다.
- Docker 이미지 크기는 약 640MB입니다(Ubuntu 기반 JRE). 필요하면 경량 베이스 이미지로 줄일 수 있습니다.


---

## 9. AI 활용 내역

구현 과제에서 AI 도구를 활용했습니다.

- **도구**: Claude Code (Anthropic Claude)
- **활용 방식**: 터미널에서 대화하며 단계마다 "무엇을 만들지 설명 → 작성 → 검증 → 커밋 전 점검" 순서로 진행했습니다. 모든 커밋은 파일 목록과 메시지를 확인한 뒤 진행했습니다.

| 영역 | 담당                                                                                |
|---|-----------------------------------------------------------------------------------|
| 설계 논의 | 초기 설계는 직접 했고, 장단점에 따른 선택의 과정에 AI를 활용했습니다. 자세한 커밋 기록을 위해 작업을 단계로 나누는 데 AI를 활용했습니다. |
| 핵심 구현 코드: 엔티티·리포지토리, 정규화(`SearchNormalizer`), 엑셀 값 변환(`FoodRowConverter`), 엑셀 리더, upsert 적재와 Runner, 예외 처리, 등록·조회·수정·삭제·검색 API(DTO, 서비스, 컨트롤러, Specification) | 직접 작성 후 AI가 결과 검토                                                                 |
| 테스트 코드(정규화, 값 변환, 리더, 적재, CRUD·검색, 예외 처리, 요청 추적, OpenAPI) | 리포지토리 통합 테스트는 직접 작성했습니다. 그 외에는 핵심 구현 코드를 작성한 뒤 AI가 작성한 테스트 코드로 검증했습니다.            |
| 설정·배포: Flyway 스키마, Dockerfile, docker-compose, GitHub Actions CI, Swagger 설정, 요청 로그 필터 | 논의와 의사결정은 직접 했고, 구현은 AI가 했습니다.                                                    |
| 코드 검토와 버그 발견 | 상세한 프롬프트로 AI가 버그를 찾게 했습니다. 수정 방법은 제가 여러 선택지를 AI에게 제안하고, 적절한 방법을 골라 직접 수정했습니다.     |
| 측정(엑셀 읽기 메모리, 적재 시간, 인덱스 `EXPLAIN`) | 인덱스 추가 도입 여부를 판단하기 위해 측정을 요청했고, AI가 수행했습니다.                                       |

**AI가 검토로 찾아낸 문제 예**
- 엑셀 헤더 상수가 실제 파일과 달라 값이 조용히 `NULL`이 되던 문제(실제 헤더와 대조해 수정)
- upsert SQL의 컬럼 이름 오류(컴파일러는 못 잡고 통합 테스트로 발견)
- 마지막 `Exception` 핸들러가 404·405·415를 500으로 바꾸던 문제
- 헬스체크가 `UP`이어도 적재가 끝나지 않아 빈 결과가 나오던 기동 순서 문제(readiness 프로브로 해결)

---

## 10. 소감

- **데이터를 먼저 확인하는 일이 가장 중요했습니다.** 단순히 엑셀을 읽어서 DB에 밀어넣으면 끝인 줄 알았지만, 실제 파일은 안내문과 건수도 달랐고(9만 105건), 빈칸 대신 -가 들어있거나 1g 미만 같은 문자열이 섞여 있었습니다. 이걸 NULL로 처리할지 다른 값으로 대체할지 정책을 정하고 그 근거를 README에 남기면서, "데이터 처리는 코드 작성보다 정책을 정하는 일이 더 크구나"를 체감했습니다.
- **성능과 메모리는 추측하지 않고 측정하려 했습니다.** 2.7MB 엑셀을 일반 방식으로 읽으면 약 730MB 힙이 필요해 `OutOfMemoryError`가 났고, 스트리밍 읽기로 바꿨습니다. 인덱스도 처음에는 9만 건을 가정해 더 도입하려 했으나, AI에게 92,196건 규모로 `EXPLAIN ANALYZE` 측정을 요청해 확인해 보니 필요하지 않아 추가하지 않았습니다. 다만 합성 데이터로 측정했고 인덱스가 없을 때와의 비교는 하지 못한 점이 아쉽습니다.
- **AI와 협엽하는 방식을 사용했습니다.** 핵심 코드는 직접 작성하고, AI에게는 설계 선택지 비교, 코드 검토, 테스트 작성, 측정, 배포 설정을 맡겼습니다. AI가 만든 결과를 그대로 받아들이지 않고 커밋 전에 점검하며 이유를 설명할 수 있을 때까지 점검했습니다. 실제로 트랜잭션 위치나 배치 옵션 효과에 대해 AI가 잘못된 설명을 했을 때, 꼬리를 물고 질문해 AI의 답변을 정정하고 실제 측정으로 검증하는 과정을 거쳤습니다.
- **아쉬운 점과 다음에 해 볼 것**: 동시에 같은 식품코드가 들어오는 상황을 실제 동시성 테스트로 검증하지 못했고, 데이터가 훨씬 커졌을 때를 위한 cursor 방식 페이지네이션, 일정 건수마다 커밋하는 적재, 엑셀에서 지워진 행을 DB에 반영하는 동기화는 구현하지 못했습니다(8장에 정리).
