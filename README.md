# Gayadi Server

GAYADI는 여행자의 성향과 날씨·혼잡·교통 변화를 반영해 여행 전·중·후의 일정과 이동 경로를 추천하는 여행 도우미 서비스입니다.

## 핵심 경험

- 여행 전: 성향 설문을 바탕으로 여행 일정 생성
- 출발 전: 모여서 출발 또는 각자 출발에 맞는 대중교통 추천
- 여행 중: 날씨·혼잡·교통 변화 감지와 대체 일정 제안
- 여행 후: 마지막 장소에서 멤버별 귀가 경로 추천

일정 변경은 자동 확정하지 않습니다. 서버가 변경 이유와 대체안을 제시하고 사용자가 승인한 경우에만 현재 일정에 반영합니다.

## 설계 문서

- [서비스 설계서](docs/architecture/gayadi-service-design.md): 제품 흐름, 데이터 모델, API, 아키텍처, 보안, 운영 및 출시 기준
- [ERDCloud Import SQL](docs/database/gayadi-erdcloud.sql): ERD 작성을 위한 MySQL 8 형식의 논리 DDL
- [확장형 아키텍처 이미지](docs/architecture/travel-realtime-architecture.png): Worker·이벤트 큐 등을 포함한 확장 참고 구조

## MVP 기술 구성

- Android 앱
- Spring Boot modular monolith
- PostgreSQL + PostGIS
- Redis
- OAuth/OIDC
- 관광·날씨·혼잡·대중교통/경로 API
- FCM Push + SSE
- Logs, Metrics, Tracing

## 설계 원칙

- PostgreSQL은 사용자·여행·설문·일정·선택 경로의 원본 저장소입니다.
- 장소는 반복 검색과 코스 생성에 필요하므로 DB에 동기화합니다.
- 날씨·혼잡·교통의 일반 조회값과 경로 후보는 Redis에 짧게 캐시합니다.
- 일정 변경에 사용한 이벤트와 사용자가 선택한 결과만 영구 저장합니다.
- 외부 API 장애 중에도 확정 일정과 선택 경로는 조회할 수 있어야 합니다.
- 사용자 위치는 동의한 여행에서만 처리하고 여행 종료 후 삭제합니다.

현재 저장소는 서비스 설계 단계이며, 구현 시 설계서와 Flyway migration을 함께 갱신합니다.
