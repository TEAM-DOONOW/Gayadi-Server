# GAYADI Agent 연구·개발 자료

기준일: 2026-08-21

대상 구현: Java 21, Spring Boot 4.1, Spring AI 2.0.0, Groq Chat, 한국관광공사 TourAPI, PostgreSQL(H2는 테스트 전용)

이 문서는 GAYADI의 여행지 추천 Agent와 상황 대처 Agent를 구현하는 데 필요한 연구 논문과 공식 개발 자료를 선별한 문서다. 일반적인 LLM 개론보다 다음 문제에 직접 연결되는 자료를 우선한다.

- 사용자의 여행 컨텍스트를 검색 계획으로 바꾸기
- TourAPI와 날씨·경로 API를 도구로 호출하기
- 결과가 부족할 때 질의를 개선하고 다시 검색하기
- 여러 후보를 취합하고 일정 제약을 만족하는 결과를 만들기
- 도구 호출·추천·변경 제안을 평가하고 안전하게 운영하기

## 1. 결론

### 1.1 자율 Agent보다 bounded workflow로 시작한다

Anthropic의 실전 가이드와 Spring AI 가이드는 고정된 순서가 명확한 문제에는 자율 Agent보다 workflow가 더 예측 가능하다고 설명한다. GAYADI의 첫 버전은 다음처럼 제한된 Agent workflow로 시작하는 것이 적절하다.

```text
RecommendationContext 생성
  -> 검색 계획 생성
  -> TourAPI/장소 검색 도구 실행
  -> 결과 품질 확인 및 1회 질의 개선
  -> 상세 정보 보강
  -> 후보·일정 제약 검증
  -> 최종 추천 또는 변경 제안 생성
```

LLM은 검색어·콘텐츠 타입·검색 방식·재검색 여부를 결정할 수 있지만, API 키, 지역 코드, 페이지네이션, 하드 제약, DB 쓰기는 서버가 소유한다.

### 1.2 Groq 요청은 두 단계로 나눈다

Groq 공식 문서 기준으로 tool use와 Structured Outputs는 현재 한 요청에서 함께 사용할 수 없다. 따라서 다음을 분리한다.

```text
1단계: tool calling
  Groq -> searchTourPlaces 도구 호출 -> 결과를 다시 Groq에 전달

2단계: strict structured output
  후보 목록 -> Groq -> CandidateDecision JSON
```

`openai/gpt-oss-20b`는 Groq 도구 호출과 strict structured output 모두에 사용할 수 있지만, 두 기능을 같은 요청에 섞지 않는다. 도구 호출에 여러 검색어가 필요하면 모델의 parallel tool call에 의존하지 말고 `searchTourPlaces`가 검색 계획 배열을 받아 서버에서 제한적으로 실행하도록 설계한다.

### 1.3 검색 품질과 Agent 품질을 분리해 평가한다

추천이 나쁜 원인은 다음 중 하나일 수 있다.

- Agent가 잘못된 검색어·API·필터를 선택함
- TourAPI 또는 장소 카탈로그에 후보가 없음
- 검색 결과는 좋지만 그룹·날씨·거리 점수가 잘못됨
- 후보는 맞지만 LLM이 잘못 선택하거나 사실을 만들어냄
- 일정 저장·버전 검사·변경 승인 흐름이 실패함

따라서 `Recall@K`, `nDCG@K` 같은 검색 지표와 `tool-call validity`, `constraint satisfaction`, `proposal correctness`, `pass^k`를 별도 측정한다.

## 2. GAYADI 적용 대상

| 대상 | 현재 코드 | Agent가 할 일 | 서버가 할 일 |
| --- | --- | --- | --- |
| 추천 Agent | `recommendation` | 사용자 의도·성향으로 검색 계획, 검색어 확장, 후보 취합, 추천 코스 초안 | 지역·인원·날씨 컨텍스트, 하드 필터, 최종 검증 |
| 상황 대처 Agent | `event`, `schedule` | 날씨·혼잡·교통·대중교통 누락에 맞는 대체 검색 계획과 변경 이유 생성 | 미래 일정만 변경, `version` 충돌 검사, 승인 전에는 DB 미변경 |
| 검색 도구 | `tourapi`, `place` | 도구를 언제 호출할지 결정 | TourAPI 호출, 페이지네이션, 캐시, upsert, rate limit |
| 검색 인덱스 | `PlaceEmbeddingService` | 필요 시 semantic query 작성 | 문서 생성, 임베딩, lexical·dense 검색, 삭제·버전 관리 |
| 평가 | 신규 필요 | 후보·추천·질의에 대한 판단 보조 | 정답셋, trace, 상태 비교, 회귀 테스트 |

`PlaceRecommendationAgent`는 TourAPI API-first 경로를 사용한다. TourAPI 목록·공통 상세·소개 상세 조회, 후보 정규화, 선택 후보의 `places` 스냅샷 저장이 구현되어 있다. 임베딩 검색은 아직 구현하지 않는다.

## 3. 필독 연구 논문

### 3.1 Agent·도구 호출·계획

| 우선순위 | 자료 | 핵심 내용 | GAYADI 적용 |
| --- | --- | --- | --- |
| 필독 | [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629) | 추론과 외부 행동을 번갈아 수행하고 도구 결과로 계획을 갱신한다. | 검색 계획 → TourAPI 도구 → 결과 확인 → 재검색 loop의 기본 모델 |
| 필독 | [TravelPlanner: A Benchmark for Real-World Planning with Language Agents](https://arxiv.org/abs/2402.01622) | 여행 계획에서 여러 제약·도구·장기 상태 추적이 어렵고, 최신 LLM도 성공률이 낮음을 보여준다. | 여행 날짜, 인원, 이동, 운영시간, 날씨를 단순 프롬프트가 아닌 검증 가능한 제약으로 모델링 |
| 필독 | [RecMind: Large Language Model Powered Agent For Recommendation](https://arxiv.org/abs/2308.14296) | 외부 지식과 도구를 사용하는 개인화 추천 Agent와 계획 개선 방식을 제안한다. | 사용자 성향과 TourAPI 검색을 결합하고 중간 상태를 다음 검색에 반영 |
| 필독 | [Toolformer: Language Models Can Teach Themselves to Use Tools](https://arxiv.org/abs/2302.04761) | 어떤 API를 언제 호출하고 인자를 어떻게 만들지 학습하는 관점을 제시한다. | `searchTourPlaces` 도구 설명·예시·인자 스키마를 설계하는 기준 |
| 권장 | [Gorilla: Large Language Model Connected with Massive APIs](https://arxiv.org/abs/2305.15334) | API 문서 검색과 결합해 잘못된 API 인자·호출을 줄인다. | TourAPI 오퍼레이션·콘텐츠 타입·지역 코드 문서를 검색 계획에 반영 |
| 권장 | [ToolLLM](https://arxiv.org/abs/2307.16789) | 다수의 실제 API, 다단계 호출 경로, API 선택 평가를 다룬다. | 이후 TourAPI·날씨·경로·장소 DB 도구가 늘어날 때 tool router와 ToolEval 설계 참고 |
| 필독 | [Building Effective Agents](https://www.anthropic.com/research/building-effective-agents) | chain, routing, parallelization, orchestrator-workers, evaluator-optimizer와 Agent의 선택 기준을 제시한다. | 처음에는 chain/evaluator-optimizer workflow로 시작하고, 검색 횟수를 예측하기 어려울 때만 bounded Agent로 확장 |

### 3.2 질의 개선·RAG·검색

| 우선순위 | 자료 | 핵심 내용 | GAYADI 적용 |
| --- | --- | --- | --- |
| 필독 | [Query Rewriting for Retrieval-Augmented Large Language Models](https://arxiv.org/abs/2305.14283) | 검색 전에 LLM이 질의를 재작성하는 Rewrite-Retrieve-Read 구조를 제안한다. | “비 오는 날 울산에서 단체로 갈 실내 장소”를 TourAPI용 구체 키워드와 콘텐츠 타입으로 변환 |
| 권장 | [Query2doc](https://arxiv.org/abs/2303.07678) | LLM이 가상 문서를 생성해 sparse·dense 검색 질의를 확장한다. | TourAPI keyword 검색보다 장소 카탈로그가 커진 뒤 query expansion 실험에 사용 |
| 권장 | [HyDE](https://arxiv.org/abs/2212.10496) | 질의에서 가상 문서를 만든 뒤 실제 문서 임베딩 공간으로 검색한다. 한국어·다국어 결과도 포함한다. | semantic 장소 검색을 추가할 때 실험 후보. 생성된 설명을 사실로 사용하지 않고 검색 벡터로만 사용 |
| 필독 | [BEIR](https://arxiv.org/abs/2104.08663) | lexical, dense, late-interaction, reranking을 다양한 도메인에서 비교한다. BM25가 강한 baseline임을 보여준다. | 장소명·고유명사는 lexical, 의도 검색은 dense, 최종은 hybrid로 평가 |
| 권장 | [Modular RAG](https://arxiv.org/abs/2407.21059) | RAG를 routing, scheduling, fusion, loop 모듈로 나누고 linear·conditional·branching·looping 패턴을 정리한다. | TourAPI, DB, vector 검색을 하나의 거대 프롬프트가 아닌 모듈별 retriever/tool로 구성 |
| 권장 | [Self-RAG](https://arxiv.org/abs/2310.11511) | 항상 검색하지 않고 필요할 때 검색하며 검색 결과와 생성 결과를 비판한다. | 후보 수·필드 누락·제약 충족률이 낮을 때만 재검색하는 판단 로직 참고 |
| 후순위 | [DPR](https://arxiv.org/abs/2004.04906), [ColBERT](https://arxiv.org/abs/2004.12832) | dense dual encoder와 late interaction 검색 구조를 다룬다. | 장소 수가 커져 cross-encoder/late-interaction reranker가 필요할 때 참고 |

### 3.3 임베딩·추천 모델 선택

| 자료 | 핵심 내용 | GAYADI 적용 |
| --- | --- | --- |
| [MTEB](https://arxiv.org/abs/2210.07316) | 임베딩을 유사도 하나가 아니라 retrieval, reranking, clustering 등 여러 task·언어로 평가한다. | 한국어 장소 질의에 모델을 고를 때 영어 점수만 보고 선택하지 않기 |
| [E5](https://arxiv.org/abs/2212.03533) | weakly supervised contrastive pretraining으로 다국어 검색 임베딩을 만든다. query/passage prefix 사용이 중요하다. | 별도 임베딩 서버를 둘 경우 `multilingual-e5` baseline으로 평가 |
| [BGE-M3](https://arxiv.org/abs/2402.03216) | 100개 이상 언어, dense·sparse·multi-vector를 지원하고 hybrid + reranking을 권장한다. | 한국어 장소 검색 후보. 현재 Groq는 임베딩 제공자가 아니므로 별도 local/embedding service 후보 |

## 4. 평가·안전 논문

| 우선순위 | 자료 | 핵심 내용 | GAYADI 적용 |
| --- | --- | --- | --- |
| 필독 | [τ-bench](https://arxiv.org/abs/2406.12045) | 도메인 정책과 도구를 가진 Agent를 사용자와 상호작용시키고 최종 DB 상태를 비교한다. `pass^k`로 반복 신뢰성을 측정한다. | 상황 대처 Agent가 제안 상태·승인 상태·버전 상태를 올바르게 만드는지 테스트 |
| 필독 | [TravelPlanner](https://arxiv.org/abs/2402.01622) | 여행 계획의 다중 제약 실패를 측정한다. | GAYADI 전용 시나리오셋의 기준 설계 |
| 권장 | [AgentBench](https://arxiv.org/abs/2308.03688) | 여러 환경에서 LLM Agent의 장기 추론·의사결정·도구 사용 실패를 평가한다. | Tool call success, instruction following, multi-turn failure를 분리 측정 |
| 필독 | [Ragas](https://arxiv.org/abs/2309.15217) | RAG의 context relevance, faithfulness, answer quality를 reference-free로 평가한다. | 후보 문맥이 추천 이유를 지지하는지 자동 평가 |
| 필독 | [AgentDojo](https://arxiv.org/abs/2406.13352) | 외부 도구 데이터의 prompt injection과 방어를 동적 환경에서 평가한다. | TourAPI의 설명·운영시간·외부 텍스트를 지시문이 아닌 데이터로 취급하고 tool 결과 injection 테스트 |
| 권장 | [OWASP Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) | prompt injection, 민감정보 노출, improper output handling, excessive agency, vector weakness, unbounded consumption 등을 정리한다. | API key 비노출, 개인 정보 최소화, tool allowlist, 호출 예산, ID 검증, vector metadata ACL |
| 권장 | [NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework) | AI 위험을 Govern, Map, Measure, Manage 관점으로 관리한다. | 추천 품질·개인정보·외부 API 장애·변경 승인 책임을 운영 기준으로 기록 |

## 5. 공식 개발 자료

### 5.1 Groq

- [OpenAI Compatibility](https://console.groq.com/docs/openai): OpenAI client의 `base_url`을 `https://api.groq.com/openai/v1`로 바꾸는 방식이다.
- [Tool Use](https://console.groq.com/docs/tool-use): tool definition → tool call → 애플리케이션 실행 → 결과 반환 → 다음 tool call 또는 최종 응답의 loop를 설명한다.
- [Structured Outputs](https://console.groq.com/docs/structured-outputs): `openai/gpt-oss-20b`, `openai/gpt-oss-120b`의 strict JSON schema 사용법과 제약을 설명한다.
- [Models](https://console.groq.com/docs/models): 모델별 tool calling, parallel tool use, structured output 지원 여부를 확인한다.
- [Rate Limits](https://console.groq.com/docs/rate-limits): Agent loop의 호출 예산과 fallback을 설계할 때 확인한다.

현재 키 테스트 결과는 `/v1/models`와 `openai/gpt-oss-20b` chat 호출이 성공했다. Groq 모델 목록에서는 임베딩 모델을 확인하지 못했으므로 LLM과 임베딩 provider를 분리한다.

### 5.2 Spring AI

- [Groq Chat](https://docs.spring.io/spring-ai/reference/api/chat/groq-chat.html): 기존 `spring-ai-starter-model-openai`를 사용해 Groq base URL·키·모델을 설정하는 방법을 설명한다.
- [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html): `@Tool`, `ToolCallback`, `ToolContext`, `ToolCallingAdvisor`, tool loop의 기본 API다.
- [ToolCallingAdvisor](https://docs.spring.io/spring-ai/reference/api/tools/tool-calling-advisor.html): loop hook, custom advisor, memory 위치, tool call limit, 관찰·예산 제어를 설명한다.
- [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html): chain, routing, parallelization, orchestrator-workers, evaluator-optimizer 예제다.
- [RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html): `RewriteQueryTransformer`, `MultiQueryExpander`, document joiner, metadata filter, reranking post-processor를 제공한다.
- [Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html): `VectorStoreRetriever`와 `VectorStore`의 읽기·쓰기 분리, metadata filter, `SimpleVectorStore`의 개발용 한계를 설명한다.
- [Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html): `RelevancyEvaluator`, `FactCheckingEvaluator`와 통합 테스트 예제를 제공한다.

현재 프로젝트 dependency는 Spring AI 2.0.0이고 공식 문서는 2.0.1 기준이다. 구현 전에 실제 dependency가 제공하는 API와 property 이름을 확인한다.

### 5.3 검색 저장소·임베딩

- [pgvector](https://github.com/pgvector/pgvector): PostgreSQL의 vector type, HNSW/IVFFlat, metadata filter, hybrid search와 RRF/cross-encoder 예제를 제공한다.
- [OpenSearch Hybrid Search](https://docs.opensearch.org/latest/vector-search/ai-search/hybrid-search/index/): BM25와 neural search를 하나의 검색 계층에서 결합할 때 참고한다.
- [BGE-M3 model card](https://huggingface.co/BAAI/bge-m3): dense·sparse·ColBERT 계열 출력을 함께 만들고 hybrid + reranking하는 방법을 제공한다.
- [multilingual-e5-large model card](https://huggingface.co/intfloat/multilingual-e5-large): `query:`와 `passage:` prefix, 한국어를 포함한 다국어 검색 사용법과 제한을 설명한다.

### 5.4 TourAPI·프로토콜

- 저장소의 [TourAPI manual](../tourapi-manual/manual_v4.4.txt)은 `areaBasedList2`, `locationBasedList2`, `searchKeyword2`, `detailCommon2`, `detailIntro2`, `detailInfo2`, 동기화 API의 공식 명세와 예시를 포함한다.
- 저장소의 `TourApiController`와 `TourApiService`는 목록 API를 외부에 제공하고, Agent 내부에서 공통 상세·소개 상세 API를 사용한다. 전체 `contentId` 동기화와 상세 결과 캐시 정책은 추가 운영 작업이다.
- TourAPI 매뉴얼은 `ContentTypeId=40` 교통 카테고리가 삭제되었다고 명시한다. 실시간 버스 도착·노선·다음 차량은 TourAPI가 아닌 별도 대중교통 공급자와 `RouteProvider`로 연결한다.
- [Model Context Protocol specification](https://modelcontextprotocol.io/specification/2025-06-18)은 외부 도구를 표준화하지만, 현재는 Spring AI `@Tool`로 내부 TourAPI 도구를 구현하는 편이 단순하다. 여러 애플리케이션이 같은 도구 서버를 공유해야 할 때 MCP를 검토한다.

## 6. GAYADI에 적용할 설계

### 6.1 Agent 호출 단계

```text
RecommendationContextBuilder
  - trip, joined members, personality axes
  - weather policy, date/time, existing plan

SearchPlanner
  - query variants
  - TourAPI operation
  - content types
  - location/radius
  - required constraints

TourSearchTool
  - region-code resolution
  - TourAPI list/detail calls
  - pagination, cache, timeout, dedupe

CandidatePolicy
  - indoor, region, status, visibility, hours
  - group size, distance, route feasibility

CandidateJudge
  - allowed candidate IDs only
  - structured ranking and reasons

PlanDraft / ChangeProposal
  - never direct mutation from LLM
  - version and approval checks
```

### 6.2 도구는 하나의 큰 API보다 의미 단위로 만든다

첫 버전 도구는 세 개면 충분하다.

```text
searchTourPlaces(SearchRequest)
getTourPlaceDetails(DetailRequest)
getCurrentTripConstraints(TripConstraintRequest)
```

`searchTourPlaces`는 raw URL이나 SQL을 받지 않고 `operation`, `regionName`, `keywords`, `contentTypeIds`, `anchorPlaceId`, `radiusMeters`, `maxPages`만 받는다. `getCurrentTripConstraints`의 사용자 ID·여행 권한은 Spring AI `ToolContext`로 전달해 모델 입력에 넣지 않는다.

### 6.3 Groq와 Spring AI의 단계별 사용

```text
Tool phase
  ChatClient + @Tool(searchTourPlaces, getTourPlaceDetails)
  ToolCallingAdvisor
  max total tool calls = 3~5

Decision phase
  ChatClient + strict JSON schema
  placeId는 후보 목록 enum 또는 서버 검증
  response: selectedPlaceIds, order, reasons, warnings
```

Tool phase와 strict structured output phase를 분리하면 Groq 호환성 제약을 피하고, 마지막 응답을 Java record로 안전하게 파싱할 수 있다.

## 7. 평가 데이터와 테스트 산출물

논문을 읽는 것보다 GAYADI 전용 시나리오셋을 먼저 만드는 것이 중요하다.

| 산출물 | 초기 목표 | 검증 내용 |
| --- | --- | --- |
| 검색 질의셋 | 50개 | 울산·서울·제주, 키워드·성향·날씨·인원 조합 |
| 정답 후보셋 | 질의별 3~10개 | 사람이 적합·부적합·필수조건 위반을 라벨링 |
| 개선 시나리오 | 20개 | 비·폭염·폐쇄·혼잡·교통 지연에 대한 대체 장소 |
| tool trace | 모든 호출 | 검색 계획, 인자, 결과 수, latency, 재시도, 오류 |
| state assertion | 모든 변경 시나리오 | 일정 version, 승인 전 무변경, 승인 후 미래 일정만 변경 |
| adversarial set | 20개 이상 | 장소 설명에 지시문 삽입, 잘못된 ID, 타 지역 후보, 과도한 호출 |

최소 지표는 다음과 같다.

- Retrieval: `Recall@10`, `Recall@20`, `MRR`, `nDCG@10`, zero-result rate
- Agent: valid tool-call rate, wrong-tool rate, invalid-argument rate, average tool calls, p95 latency
- Constraint: region violation rate, indoor violation rate, time conflict rate, duplicate rate, group-fit accuracy
- Generation: candidate-grounded rate, unsupported-claim rate, structured-output parse rate
- Workflow: proposal correctness, stale-version rejection rate, `pass^k`, human acceptance rate

## 8. 구현 순서

1. Groq base URL·모델 설정을 Spring AI에 분리한다. 현재 `application.yml`의 OpenAI 설정과 임베딩 의존을 chat 설정과 분리한다.
2. `RecommendationContext`와 `SearchPlan` Java record를 만든다.
3. `TourSearchTool`을 `@Tool`로 만들고 TourAPI 목록·상세·페이지네이션·호출 예산을 감싼다.
4. 울산 비 오는 날 시나리오 10개로 tool phase와 재검색 loop를 검증한다.
5. `places` upsert와 `contentId` 기반 상세 캐시를 만든다.
6. strict `CandidateDecision` 출력과 후보 ID·하드 제약 검증을 추가한다.
7. `DestinationImprovementAgent`를 `EventService`의 변경 제안 흐름에 연결한다.
8. 평가셋으로 lexical-only, hybrid, Agent query expansion을 비교한다.
9. 평가에서 개선 효과가 확인될 때만 별도 임베딩 서버·pgvector 또는 OpenSearch를 도입한다.

## 9. 당장 읽을 순서

1. [Anthropic Building Effective Agents](https://www.anthropic.com/research/building-effective-agents)
2. [ReAct](https://arxiv.org/abs/2210.03629)
3. [TravelPlanner](https://arxiv.org/abs/2402.01622)
4. [RecMind](https://arxiv.org/abs/2308.14296)
5. [Query Rewriting](https://arxiv.org/abs/2305.14283)
6. [Groq Tool Use](https://console.groq.com/docs/tool-use)와 [Structured Outputs](https://console.groq.com/docs/structured-outputs)
7. [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)과 [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html)
8. [TravelPlanner 평가 방식](https://arxiv.org/abs/2402.01622), [τ-bench](https://arxiv.org/abs/2406.12045), [AgentDojo](https://arxiv.org/abs/2406.13352)
9. [BEIR](https://arxiv.org/abs/2104.08663), [MTEB](https://arxiv.org/abs/2210.07316), [BGE-M3](https://arxiv.org/abs/2402.03216)

## 10. 현재 결정: API-first, 임베딩 없음

현재 장소 데이터가 사전 구축되어 있지 않고 추천 후보를 모두 외부 API에서 가져온다면, 1차 Agent에는 임베딩 모델과 Vector DB가 필요하지 않다. 임베딩은 검색할 문서 corpus를 미리 저장해 두고 의미 유사도 후보를 찾을 때 효과가 있으므로, 매 요청마다 API 결과를 임베딩하는 것은 비용·지연·캐시 효율 면에서 적합하지 않다.

첫 버전은 다음 구조로 고정한다.

```text
사용자·여행·날씨 컨텍스트
  -> Groq가 TourAPI SearchPlan 생성
  -> 서버가 TourAPI 목록·상세 API 실행
  -> 서버가 페이지네이션·중복 제거·하드 필터·업무 점수화
  -> Groq가 후보 중 최종 장소·순서·이유를 구조화해 반환
```

이 결정은 업무 DB 저장을 없앤다는 뜻이 아니다. 현재 스키마의 `travel_plan_items.place_id`와 일정 재조회·외부 API 장애 대응을 위해 다음 중 하나는 보존한다.

- 선택된 장소만 `places`에 upsert하고 `source_place_id`와 원본 갱신 시각을 저장한다.
- 선택된 TourAPI `contentId`와 일정에 사용한 이름·좌표·주소를 일정 스냅샷으로 저장한다.

모든 후보를 검색 인덱스에 넣지는 않되, 선택된 결과와 일정 근거는 보존해야 한다. TourAPI가 일시적으로 실패하거나 원본 정보가 바뀌어도 사용자가 확정한 일정을 다시 보여줄 수 있어야 한다.

API-first Agent의 핵심 도구는 다음 세 가지다.

```text
searchTourPlaces(SearchPlan)
getTourPlaceDetails(contentIds)
getWeatherAndTripConstraints(tripId, targetAt)
```

`searchTourPlaces`는 `searchKeyword2`만 사용하지 않고 `areaBasedList2`, `locationBasedList2`, `searchKeyword2`를 목적에 따라 선택한다. LLM은 “울산 실내 단체 체험”, “울산 박물관”, “울산 쇼핑몰”처럼 질의를 확장하지만, 지역 코드·페이지·호출 수·하드 조건은 서버가 통제한다.

임베딩은 다음 조건이 생길 때 재검토한다.

- 여러 지역의 장소가 누적되어 TourAPI 요청만으로 latency와 quota를 감당하기 어려울 때
- 자연어 의도 검색이 keyword 확장보다 유의미하게 개선된다는 평가 결과가 있을 때
- TourAPI 결과를 장기 캐시·정규화해 자체 장소 corpus가 생겼을 때
