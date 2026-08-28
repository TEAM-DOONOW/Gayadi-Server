package com.gayadi.server.config;

import com.gayadi.server.common.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gayadiOpenApi() {
        Components components = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인으로 발급받은 토큰을 입력하세요."));
        ModelConverters.getInstance().read(ApiErrorResponse.class)
                .forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("가야디 API")
                        .description("함께 떠나는 여행의 설문, 일정, 경로와 현장 대응을 제공하는 API입니다.")
                        .version("v1"))
                .components(components)
                .tags(List.of(
                        tag("인증", "가입, 로그인과 사용자 인증"),
                        tag("사용자", "사용자 정보 관리"),
                        tag("여행", "여행과 참여자 관리"),
                        tag("설문", "여행 성향 문항과 응답 관리"),
                        tag("일정", "여행 일정 생성과 변경"),
                        tag("경로", "출발, 이동과 귀가 경로 안내"),
                        tag("날짜 조율", "그룹 여행 참여자의 가능한 날짜 조율"),
                        tag("여행 경비", "여행 지출, 공동 경비와 참여자 정산"),
                        tag("현장 상황", "날씨와 돌발 상황 대응"),
                        tag("장소", "여행 장소 조회"),
                        tag("추천", "여행 장소 추천"),
                        tag("초대", "여행 초대 발급과 참여"),
                        tag("찜", "사용자가 저장한 장소 관리"),
                        tag("법률 문서", "이용약관과 개인정보처리방침 조회"),
                        tag("공지", "앱 업데이트와 서비스 공지"),
                        tag("문의", "고객지원 문의 접수"),
                        tag("관리", "운영 자료 관리"),
                        tag("관광 API", "한국관광공사 국문 관광정보 서비스(KorService2) 연동"),
                        tag("날씨 API", "기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) 연동")
                ));
    }

    @Bean
    OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("400", errorResponse("요청값 오류"));
            operation.getResponses().addApiResponse("404", errorResponse("자료 없음"));
            operation.getResponses().addApiResponse("409", errorResponse("현재 상태와 충돌"));
            operation.getResponses().addApiResponse("415", errorResponse("지원하지 않는 본문 형식"));
            operation.getResponses().addApiResponse("429", errorResponse("요청 횟수 초과"));
            operation.getResponses().addApiResponse("500", errorResponse("서버 오류"));
            operation.getResponses().addApiResponse("502", errorResponse("외부 API 응답 오류"));
            operation.getResponses().addApiResponse("503", errorResponse("선택 기능을 사용할 수 없음"));
            if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                operation.getResponses().addApiResponse("401", errorResponse("로그인 필요"));
                operation.getResponses().addApiResponse("403", errorResponse("권한 부족"));
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer jsonSuccessResponseMediaTypes() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) return;
                operation.getResponses().values().forEach(response -> {
                    Content content = response.getContent();
                    if (content == null || content.containsKey("application/json")) return;
                    MediaType wildcard = content.remove("*/*");
                    if (wildcard != null) content.addMediaType("application/json", wildcard);
                });
            }));
        };
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
