package com.gayadi.server.config;

import com.gayadi.server.common.response.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI gayadiOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                `POST /api/v1/auth/google-tokens` 또는 `POST /api/v1/auth/tokens` 응답의 \
                                `accessToken`을 `Bearer {token}`으로 넣습니다. \
                                잘못된 토큰은 401 AUTH_TOKEN_INVALID, 만료는 401 AUTH_TOKEN_EXPIRED, \
                                탈퇴 계정은 403 AUTH_ACCOUNT_UNAVAILABLE입니다."""));
        ModelConverters.getInstance().read(ApiErrorResponse.class)
                .forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("가야디 API")
                        .description("""
                                함께 떠나는 여행의 설문, 일정, 경로와 현장 대응을 제공하는 API입니다.

                                앱 로그인은 Google ID 토큰을 `POST /api/v1/auth/google-tokens`로 교환해 \
                                서버 JWT를 받습니다. 이메일 가입·로그인은 개발용입니다. \
                                보호 API는 Authorization 헤더의 Bearer JWT로 사용자를 식별합니다. \
                                프로필 조회·수정·탈퇴는 `/api/v1/users/current`입니다.

                                Agent는 `APP_AI_ENABLED=true`일 때 동작합니다. \
                                `POST /api/v1/recommendations/places`는 맞춤 장소 추천이고, \
                                `POST /api/v1/trips/{tripId}/situation-responses`는 여행 상황 대처입니다. \
                                설문이 없어도 상황 대처는 동작하며, 여행 중이면 승인 가능한 변경안을 만듭니다. \
                                Agent가 꺼져 있으면 503 `RECOMMENDATION_UNAVAILABLE` 또는 \
                                `SITUATION_AGENT_UNAVAILABLE`입니다.

                                소유자 출발·귀가는 여행 생성의 `departurePlaceId`/`returnPlaceId` 또는 \
                                `PATCH /api/v1/trips/{tripId}/participants/current`로 넣습니다.

                                경로는 명사 자원입니다. 장소·혼잡 통합 조회는 `GET /api/v1/tour/areas`만 사용합니다. \
                                오류는 공통 `ApiErrorResponse`입니다.""")
                        .version("v1"))
                .components(components)
                .tags(List.of(
                        tag("인증", "Google 로그인과 개발용 이메일 계정 토큰 발급"),
                        tag("사용자", "현재 로그인한 사용자의 프로필 조회·수정·탈퇴"),
                        tag("여행", "여행 생성·참여자·내 출발·귀가 장소 관리"),
                        tag("설문", "여행 성향 문항과 응답 관리"),
                        tag("일정", "여행 일정 생성과 변경"),
                        tag("경로", "출발, 이동과 귀가 경로 안내"),
                        tag("날짜 조율", "그룹 여행 참여자의 가능한 날짜 조율"),
                        tag("여행 경비", "여행 지출, 공동 경비와 참여자 정산"),
                        tag("여행 홈", "여행, 참여자, 일정과 변경 제안을 한 번에 조회"),
                        tag("현장 상황", "날씨와 돌발 상황 대응"),
                        tag("상황 대처", "여행 상황 대처 Agent. APP_AI_ENABLED=true 필요"),
                        tag("장소", "여행 장소 조회"),
                        tag("추천", "맞춤 장소 추천 Agent. APP_AI_ENABLED=true 필요"),
                        tag("초대", "여행 초대 발급과 참여"),
                        tag("찜", "사용자가 저장한 장소 관리"),
                        tag("친구", "친구 검색·요청·수락·거절"),
                        tag("법률 문서", "이용약관과 개인정보처리방침 조회"),
                        tag("공지", "앱 업데이트와 서비스 공지"),
                        tag("문의", "고객지원 문의 접수"),
                        tag("관리", "운영 자료 관리"),
                        tag("관광 API", "한국관광공사 국문 관광정보 서비스(KorService2) 연동"),
                        tag("날씨 API", "기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) 연동"),
                        tag("혼잡", "관광지 집중률 예측")
                ));
    }

    @Bean
    OperationCustomizer documentAuthAndHidePrincipal() {
        return (operation, handlerMethod) -> {
            hideAuthenticationPrincipal(operation, handlerMethod);
            documentNoContentSuccess(operation, handlerMethod);
            operation.getResponses().addApiResponse("400", errorResponse("요청값 오류"));
            operation.getResponses().addApiResponse("404", errorResponse("자료 없음"));
            operation.getResponses().addApiResponse("405", errorResponse("지원하지 않는 HTTP 메서드"));
            operation.getResponses().addApiResponse("406", errorResponse("제공할 수 없는 응답 형식"));
            operation.getResponses().addApiResponse("409", errorResponse("현재 상태와 충돌"));
            operation.getResponses().addApiResponse("413", errorResponse("요청 데이터 크기 초과"));
            operation.getResponses().addApiResponse("415", errorResponse("지원하지 않는 본문 형식"));
            operation.getResponses().addApiResponse("429", errorResponse("요청 횟수 초과"));
            operation.getResponses().addApiResponse("500", errorResponse("서버 오류"));
            operation.getResponses().addApiResponse("502", errorResponse("외부 API 응답 오류"));
            operation.getResponses().addApiResponse("503", errorResponse("선택 기능을 사용할 수 없음"));
            if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                operation.getResponses().addApiResponse("401", errorResponse(
                        "로그인 필요. 잘못된 토큰은 AUTH_TOKEN_INVALID, 만료는 AUTH_TOKEN_EXPIRED"));
                operation.getResponses().addApiResponse("403", errorResponse(
                        "권한 부족 또는 탈퇴한 계정(AUTH_ACCOUNT_UNAVAILABLE)"));
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer jsonSuccessResponseMediaTypes() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                applyPathSecurity(path, method, operation);
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses().values().forEach(response -> {
                    Content content = response.getContent();
                    if (content == null || content.containsKey("application/json")) {
                        return;
                    }
                    MediaType wildcard = content.remove("*/*");
                    if (wildcard != null) {
                        content.addMediaType("application/json", wildcard);
                    }
                });
            }));
        };
    }

    private void hideAuthenticationPrincipal(Operation operation, HandlerMethod handlerMethod) {
        Set<String> hidden = new LinkedHashSet<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.getParameterAnnotation(AuthenticationPrincipal.class) == null) {
                continue;
            }
            String name = parameter.getParameter().getName();
            if (name != null && !name.isBlank()) {
                hidden.add(name);
            }
        }
        if (operation.getParameters() == null || hidden.isEmpty()) {
            return;
        }
        operation.setParameters(operation.getParameters().stream()
                .filter(parameter -> parameter.getName() == null || !hidden.contains(parameter.getName()))
                .toList());
    }

    private void documentNoContentSuccess(Operation operation, HandlerMethod handlerMethod) {
        ResponseStatus responseStatus = handlerMethod.getMethodAnnotation(ResponseStatus.class);
        if (responseStatus == null || responseStatus.value() != HttpStatus.NO_CONTENT) {
            return;
        }
        operation.getResponses().addApiResponse("204", new ApiResponse().description("본문 없이 처리했습니다."));
        operation.getResponses().remove("200");
    }

    private void applyPathSecurity(String path, PathItem.HttpMethod method, Operation operation) {
        if (isPublic(path, method)) {
            operation.setSecurity(List.of());
            return;
        }
        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        }
        if (operation.getResponses() != null && operation.getResponses().get("401") == null) {
            operation.getResponses().addApiResponse("401", errorResponse(
                    "로그인 필요. 잘못된 토큰은 AUTH_TOKEN_INVALID, 만료는 AUTH_TOKEN_EXPIRED"));
            operation.getResponses().addApiResponse("403", errorResponse(
                    "권한 부족 또는 탈퇴한 계정(AUTH_ACCOUNT_UNAVAILABLE)"));
        }
    }

    private boolean isPublic(String path, PathItem.HttpMethod method) {
        if (path.startsWith("/api/v1/auth/registrations")
                || path.startsWith("/api/v1/auth/tokens")
                || path.startsWith("/api/v1/auth/google-tokens")
                || path.startsWith("/api/openapi")
                || path.startsWith("/api/docs")) {
            return true;
        }
        if (method != PathItem.HttpMethod.GET) {
            return false;
        }
        return path.startsWith("/api/v1/surveys/")
                || path.startsWith("/api/v1/places")
                || "/api/v1/tour/areas".equals(path)
                || path.startsWith("/api/v1/legal-documents/")
                || path.startsWith("/api/v1/notices");
    }

    private ApiResponse errorResponse(String description) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiErrorResponse");
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
