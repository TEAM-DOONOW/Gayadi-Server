package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.JsonSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Groq를 검색 계획 생성기와 후보 판정기로 사용하는 bounded Agent 모델입니다. */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class GroqRecommendationLanguageModel implements RecommendationLanguageModel {

    private final ChatClient chatClient;
    private final JsonSupport json;

    public GroqRecommendationLanguageModel(ChatClient chatClient, JsonSupport json) {
        this.chatClient = chatClient;
        this.json = json;
    }

    @Override
    public PlaceSearchPlan createSearchPlan(RecommendationContext context) {
        return call(PlaceSearchPlan.class, """
                다음 여행 추천 요청을 TourAPI 검색 계획으로 변환하세요.
                반드시 JSON 형식으로만 응답하세요.

                목적: %s
                목적지: %s
                TourAPI 시도 코드: %s
                TourAPI 시군구 코드: %s
                사용자 성향: %s
                검색어: %s
                인원수: %d
                기준 위치: 위도 %.6f, 경도 %.6f
                대상 시각: %s
                상황: %s
                정책: %s

                규칙:
                - operation은 AREA, LOCATION, KEYWORD 중 하나만 사용하세요.
                - 실내가 필요한 상황이면 contentTypeIds에 다음 코드를 우선 사용하세요: %s.
                - keywords는 TourAPI가 이해할 구체적인 한국어 검색어로 1~5개를 만드세요.
                - 지역 코드와 상황 정책을 임의로 완화하지 마세요.
                - 최대 2회의 검색 라운드만 계획하세요.
                """.formatted(
                context.purpose(), context.destination(), context.regionCode(), context.sigunguCode(),
                context.profile(), String.join(", ", context.keywords()), context.groupSize(),
                context.latitude(), context.longitude(), context.targetAt(),
                json.write(context.situation()), context.policy().summary(),
                String.join(", ", TourContentType.indoorRecommendationCodes())));
    }

    @Override
    public PlaceSearchPlan refineSearchPlan(RecommendationContext context,
                                            PlaceSearchPlan previousPlan,
                                            List<TourPlaceCandidate> candidates) {
        return call(PlaceSearchPlan.class, """
                이전 TourAPI 검색 결과가 충분하지 않습니다. 검색 계획을 한 번만 개선하세요.
                반드시 JSON 형식으로만 응답하세요.

                여행 컨텍스트: %s
                이전 계획: %s
                현재 후보: %s

                규칙:
                - 기존 하드 정책(%s)을 유지하세요.
                - 검색어를 더 구체적이거나 동의어가 포함된 한국어 표현으로 바꾸세요.
                - operation과 contentTypeIds를 목적에 맞게 조정할 수 있습니다.
                - maxSearchRounds는 1로 설정하세요.
                """.formatted(
                json.write(context), json.write(previousPlan), json.write(candidates),
                context.policy().summary()));
    }

    @Override
    public CandidateDecision decide(RecommendationContext context,
                                    List<TourPlaceCandidate> candidates) {
        return call(CandidateDecision.class, """
                허용된 후보 장소 중에서 여행 목적에 맞는 장소를 선택하세요.
                반드시 JSON 형식으로만 응답하고, 후보에 없는 placeId를 만들지 마세요.

                여행 컨텍스트:
                %s

                허용된 후보:
                %s

                규칙:
                - 최대 %d개를 선택하세요.
                - 정책상 실내가 필요한 경우 indoor=true 후보만 선택하세요.
                - 대중교통 문제가 있으면 가까운 후보와 이동 부담이 낮은 후보를 우선하세요.
                - 추천 이유는 후보 데이터에 근거한 한 문장으로 작성하세요.
                - reasoning은 전체 판단을 한국어 한두 문장으로 작성하세요.
                """.formatted(json.write(context), json.write(candidates),
                Math.min(context.limit(), Math.max(1, candidates.size()))));
    }

    private <T> T call(Class<T> type, String prompt) {
        try {
            T result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(type);
            if (result == null) {
                throw new BusinessException(RecommendationErrorCode.AI_RESPONSE_INVALID);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(RecommendationErrorCode.AI_REQUEST_FAILED);
        }
    }
}
