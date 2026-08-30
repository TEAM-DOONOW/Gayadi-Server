# GAYADI 발표용 초기 설계 자료

> 이 폴더의 생성된 그림은 초기 발표 시안으로 현재 Flyway 스키마나 구현 범위의 기준이 아니다.
> 현재 기준은 `HANDOFF.md`, `src/main/resources/db/migration` 및 Swagger다. 특히 Milvus·임베딩·
> OAuth/OIDC·Redis·SSE는 현재 구현되지 않았다.

세 그림은 같은 서비스를 서로 다른 관점에서 설명한다.

1. [ERD](gayadi-erd-presentation.svg): 관계형 업무 데이터와 Milvus 추천 인덱스의 연결을 보여준다. [draw.io 편집 원본](gayadi-erd-presentation.drawio)을 제공한다.
2. [서비스 아키텍처](gayadi-service-architecture-presentation.svg): Spring Boot 애플리케이션 내부 모듈과 DB·외부 API의 전체 연결을 보여준다. [draw.io 편집 원본](gayadi-service-architecture.drawio)을 diagrams.net에서 열어 직접 수정할 수 있다.
3. [서비스 흐름도](gayadi-service-flow-presentation.svg): 여행 전·중·후의 사용자 흐름과 각 단계의 담당 서비스를 보여준다. [draw.io 편집 원본](gayadi-service-flow-presentation.drawio)을 제공한다.

## 발표할 때 강조할 내용

- 출발 방식은 `모여서 출발`과 `각자 출발` 두 가지이며, 멤버별 출발 경로 추천에 반영한다.
- 설문은 문항별 테이블을 과도하게 나누지 않고, 하나의 설문 응답과 JSON 답변으로 단순화했다.
- 일정은 현재 확정본 하나를 유지하고 `revision_no`로 변경 이력을 구분한다. 비·혼잡 같은 변수로 대안이 생겨도 사용자가 승인하기 전에는 현재 일정을 바꾸지 않는다.
- 장소·날씨·혼잡·경로 원문 전체를 영구 저장하지 않는다. 장소 기본 정보와 실제 추천 판단에 사용한 이벤트·선택 경로만 기록한다.
- Milvus는 장소 원본 DB가 아니라 장소 설명 임베딩과 검색용 메타데이터를 저장하는 추천 인덱스다. 검색된 `place_id`로 관계형 DB의 장소 원본을 조회한다.
- 장소와 이벤트 데이터는 현재 한 DB에서 시작하지만, 규모가 커지면 별도 DB나 서비스로 분리할 수 있도록 식별자와 모듈 경계를 유지한다.

## 그림이 작성될 당시의 가정

- ERD는 Flyway V1의 실제 11개 관계형 테이블을 기준으로 하며, 별도 저장소인 Milvus의 `PLACE_VECTORS` 컬렉션을 함께 표시한다.
- Flyway V2의 성향 설문 1건과 서울 장소 4건은 현재 MVP가 사용하는 개발·시연용 기준 데이터다.
- 서비스 아키텍처는 OAuth/OIDC·관광·날씨·혼잡·대중교통·SSE·Redis를 포함한 전체 구성을 동일한 연결선으로 표현한다.

## 이미지 편집과 재생성

세 다이어그램 모두 `.drawio` 원본을 실제 diagrams.net 편집기에서 열어 수정할 수 있다.

ERD와 서비스 흐름도는 저장소 루트에서 다음 명령으로 재생성한다.

```bash
node scripts/generate-presentation-diagrams.cjs
```

PNG는 슬라이드 삽입용이고 SVG는 확대용이며, `.drawio`는 편집 원본이다.
