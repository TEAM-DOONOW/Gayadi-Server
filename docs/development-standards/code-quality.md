# 코드 품질 점검

## IDE Problems 해석

IDE의 Problems 개수는 컴파일 오류, deprecated API, 미사용 import와 프레임워크가 간접 사용하는 타입을 함께 포함할 수 있다. 숫자만 보고 삭제하지 않고 아래 순서로 확인한다.

1. Java 21 컴파일과 `-Xlint:deprecation`, `-Xlint:unchecked` 결과를 확인한다.
2. 미사용 import는 파일 내부 실제 참조 횟수로 확인한다.
3. 단독 참조 클래스는 Spring, Flyway, Logback, Jackson 등 간접 로딩 여부를 확인한다.
4. 정적 검사 후 전체 테스트로 실행 경로를 검증한다.

## 간접 사용 코드

IDE가 `never used`로 표시하더라도 아래 유형은 프레임워크가 간접 호출할 수 있으므로 사용 경로를 확인하지 않고 삭제하지 않는다.

- `@Configuration`, `@Component`, `@Repository`, `@Controller` 등 컴포넌트 스캔 대상
- Flyway 명명 규칙으로 실행되는 migration
- Logback XML에서 클래스명으로 지정하는 converter
- Jackson·Spring이 생성자나 어노테이션을 통해 사용하는 DTO

반대로 일반 private 메서드, 지역 변수와 import는 실제 참조와 테스트를 확인한 뒤 제거한다. 경고를 억제해야 한다면 억제 범위를 최소화하고 이유를 주석으로 남긴다.

## 리뷰 기준

- deprecated API는 공식 대체 API와 직렬화 결과를 확인한 뒤 교체한다.
- unchecked cast는 DB Row, JSON 또는 외부 API 경계에서 타입 검증을 보강한다.
- 긴 메서드는 줄 수만으로 분리하지 않고 책임 전환과 재사용 가능한 업무 규칙을 기준으로 판단한다.
- 중복 코드는 우연히 모양만 같은지, 동일한 변경 이유를 갖는지 확인한 뒤 추출한다.
- 정적 검사 통과만으로 기능 정상 여부를 판단하지 않고 관련 단위·통합 테스트를 실행한다.

## 검증 명령

```shell
python scripts/check_java_layout.py
.\gradlew.bat compileJava --rerun-tasks -Dorg.gradle.java.installations.paths=C:\Progra~1\Eclipse~1\jdk-21.0.12.101-hotspot --no-daemon --console=plain
.\gradlew.bat test -Dorg.gradle.java.installations.paths=C:\Progra~1\Eclipse~1\jdk-21.0.12.101-hotspot --no-daemon --console=plain
git diff --check
```
