# GAYADI 발표용 설계 자료

세 그림은 같은 서비스를 서로 다른 관점에서 설명한다.

1. [ERD](gayadi-erd-presentation.svg): 어떤 데이터를 저장하고 테이블이 어떻게 연결되는지 보여준다. `TRIPS`를 중심으로 여행 멤버, 설문 응답, 일정, 장소, 이벤트, 경로를 읽으면 된다.
2. [서비스 아키텍처](gayadi-service-architecture-presentation.svg): Spring Boot 애플리케이션 내부 모듈과 DB·외부 API의 경계를 보여준다. 실선은 현재 MVP, 점선은 운영 연동 예정 범위다. [draw.io 편집 원본](gayadi-service-architecture.drawio)을 diagrams.net에서 열어 직접 수정할 수 있다.
3. [서비스 흐름도](gayadi-service-flow-presentation.svg): 여행 전, 여행 중, 여행 후에 사용자가 경험하는 흐름과 각 단계에서 사용하는 데이터를 보여준다.

## 발표할 때 강조할 내용

- 출발 방식은 `모여서 출발`과 `각자 출발` 두 가지이며, 멤버별 출발 경로 추천에 반영한다.
- 설문은 문항별 테이블을 과도하게 나누지 않고, 하나의 설문 응답과 JSON 답변으로 단순화했다.
- 일정은 현재 확정본 하나를 유지하고 `revision_no`로 변경 이력을 구분한다. 비·혼잡 같은 변수로 대안이 생겨도 사용자가 승인하기 전에는 현재 일정을 바꾸지 않는다.
- 장소·날씨·혼잡·경로 원문 전체를 영구 저장하지 않는다. 장소 기본 정보와 실제 추천 판단에 사용한 이벤트·선택 경로만 기록한다.
- 장소와 이벤트 데이터는 현재 한 DB에서 시작하지만, 규모가 커지면 별도 DB나 서비스로 분리할 수 있도록 식별자와 모듈 경계를 유지한다.

## 현재 구현 범위

- ERD는 Flyway V1의 실제 11개 테이블과 17개 FK를 기준으로 한다.
- Flyway V2의 성향 설문 1건과 서울 장소 4건은 현재 MVP가 사용하는 개발·시연용 기준 데이터다.
- 현재 경로 계산은 로컬 스텁이며, OAuth/OIDC·관광·날씨·혼잡·대중교통·FCM/SSE·Redis는 점선으로 표시한 운영 연동 범위다.

## 이미지 편집과 재생성

서비스 아키텍처는 `gayadi-service-architecture.drawio`를 실제 diagrams.net 편집기에서 열어 수정한다. 연결선은 `배치 → 레이아웃 → 직각 라우팅`으로 정리한 뒤 PNG와 SVG를 다시 내보낸다.

ERD와 서비스 흐름도는 저장소 루트에서 다음 명령으로 재생성한다.

```bash
node scripts/generate-presentation-diagrams.cjs
```

PNG는 슬라이드 삽입용이고, SVG는 확대용이다. 서비스 아키텍처의 편집 원본은 `.drawio` 파일이다.
