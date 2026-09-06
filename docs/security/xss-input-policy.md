# Android 입력과 링크 보안 정책

GAYADI의 기본 클라이언트는 Android 네이티브 앱이다. JSON 문자열은 Android `TextView` 등에서 HTML로 실행되지 않으므로, 웹 서비스와 같은 전역 XSS 필터나 HTML sanitizer를 현재 API에 적용하지 않는다.

## 입력 유형을 먼저 선택한다

새 문자열 필드는 다음 세 유형 중 하나로 분류한다.

| 유형 | 예시 | 처리 방식 |
|---|---|---|
| 제한 문자열 | 닉네임, 상태, 코드, 날짜 | `@Pattern`, enum 등 allowlist로 잘못된 요청 거부 |
| 일반 텍스트 | 제목, 메모, 소개 | `@NotBlank`, `@Size` 등 업무 규칙을 검증하고 원문을 문자열로 저장 |
| 외부 링크 | 게시글 참고 URL | `@HttpUrl`로 HTTP·HTTPS 절대 URL만 허용 |
| HTML | 향후 WebView 기반 게시글 | 기능 도입 시 별도 위협 모델과 sanitizer 정책 설계 |

모든 문자열을 전역 필터에서 삭제하거나 HTML entity로 변환하지 않는다. 저장 계층에는 원본과 정제 결과가 섞이지 않도록 유스케이스에서 처리 방식을 명시한다.

## Android 일반 텍스트

- 서버는 HTML처럼 보이는 문자열을 임의로 삭제하거나 변환하지 않는다.
- Android는 일반 텍스트를 `TextView.setText()`처럼 코드로 실행하지 않는 API로 표시한다.
- `Html.fromHtml()`, WebView, JavaScript 평가, 동적 HTML 삽입은 일반 텍스트 출력에 사용하지 않는다.
- 제목·메모·소개는 길이, 필수 여부, 업무 규칙을 검증한다.

## 향후 게시판

기본 본문 형식은 HTML이 아닌 일반 텍스트를 권장한다. 이미지는 본문 HTML에 삽입하지 말고 업로드 메타데이터로 관리한다.

에디터와 WebView를 실제로 도입하여 HTML 본문이 필요해지면 그 때 다음을 함께 구현한다.

- 서버 allowlist sanitizer와 XSS 우회 테스트
- WebView JavaScript 기본 비활성화
- 외부 URL은 WebView 내부가 아닌 검증된 Intent로 열기
- `addJavascriptInterface` 사용 금지 또는 신뢰 콘텐츠에만 제한
- 허용 태그·속성·URL scheme을 기능 요구사항에 맞게 별도 확정

## URL

사용자가 입력하고 다른 사용자가 클릭할 수 있는 외부 링크에는 `@HttpUrl`을 사용한다. 서버가 생성하거나 신뢰하는 외부 API에서 받은 응답 URL에 기계적으로 적용하지 않는다.

```java
@Size(max = 1000)
@HttpUrl
String referenceUrl
```

이 검증은 `javascript:`, `file:`, `content:`, custom scheme과 URL userinfo를 거부한다. 클라이언트도 scheme과 host를 다시 검증하고, 민감한 작업은 검증된 Android App Link로만 연다.

서버가 해당 URL을 직접 호출하면 `@HttpUrl`만으로 충분하지 않다. SSRF 정책에서 사설·loopback·link-local 주소, DNS 재해석, redirect와 응답 크기를 추가로 제한해야 한다.

## 출력 규칙

- 일반 텍스트는 Android `TextView.setText()` 등으로 표시한다.
- URL을 열기 전에 앱에서 scheme·host를 다시 검증한다.
- JSON 응답은 `application/json`과 `X-Content-Type-Options: nosniff`를 유지한다.
- 웹 클라이언트에서는 `innerHTML`, `dangerouslySetInnerHTML`, `v-html`, `document.write`, `eval` 사용을 기본 금지한다.
- CSP는 보조 방어이며 안전한 출력과 sanitizer를 대체하지 않는다.

## 리뷰 체크리스트

- [ ] 문자열 필드의 길이와 업무 형식이 정의되었다.
- [ ] 필수 여부와 최대 길이가 정해졌다.
- [ ] 사용자가 제공한 클릭 가능 링크에 `@HttpUrl`을 사용한다.
- [ ] WebView나 HTML 본문을 추가하면 별도 보안 리뷰를 수행한다.
- [ ] 사용자 URL을 서버가 호출하면 SSRF 검토를 별도로 수행한다.
- [ ] 정상 한글·기호와 대표 XSS 우회 문자열을 함께 테스트한다.
- [ ] 프런트엔드 출력 지점이 데이터 종류에 맞는 API를 사용한다.
