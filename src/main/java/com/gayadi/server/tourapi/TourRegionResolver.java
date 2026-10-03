package com.gayadi.server.tourapi;

import com.gayadi.server.tourapi.model.LegalDistrict;

import com.gayadi.server.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 앱 지역명을 TourAPI 법정동 코드 목록으로 변환합니다. */
@Component
public class TourRegionResolver {

    private static final Map<String, List<AreaTarget>> APP_REGIONS = regions();
    /** 주소 첫 토큰(시도 약칭·정식 명칭)을 법정동 시도 코드로 바꿉니다. */
    private static final Map<String, String> SIDO_CODES = Map.ofEntries(
            Map.entry("서울", "11"), Map.entry("부산", "26"), Map.entry("대구", "27"),
            Map.entry("인천", "28"), Map.entry("광주", "29"), Map.entry("대전", "30"),
            Map.entry("울산", "31"), Map.entry("세종", "36"), Map.entry("경기", "41"),
            Map.entry("충북", "43"), Map.entry("충청북", "43"), Map.entry("충남", "44"),
            Map.entry("충청남", "44"), Map.entry("전남", "46"), Map.entry("전라남", "46"),
            Map.entry("경북", "47"), Map.entry("경상북", "47"), Map.entry("경남", "48"),
            Map.entry("경상남", "48"), Map.entry("제주", "50"), Map.entry("강원", "51"),
            Map.entry("전북", "52"), Map.entry("전라북", "52"));
    private final TourApiService tourApi;
    private final Map<String, List<LegalDistrict>> districtCache = new ConcurrentHashMap<>();

    public TourRegionResolver(TourApiService tourApi) {
        this.tourApi = tourApi;
    }

    public List<RegionCode> resolve(String regionName) {
        String normalized = normalize(regionName);
        List<AreaTarget> targets = targetsFor(normalized);
        if (targets.isEmpty()) {
            throw new BusinessException(TourApiErrorCode.TOUR_REGION_UNSUPPORTED, normalized);
        }
        List<RegionCode> result = new ArrayList<>();
        for (AreaTarget target : targets) {
            if (target.districtNames().isEmpty()) {
                result.add(new RegionCode(target.areaCode(), "", target.label()));
                continue;
            }
            List<LegalDistrict> districts = districtCache.computeIfAbsent(
                    target.areaCode(), tourApi::legalDistricts);
            List<List<RegionCode>> matchesByName = new ArrayList<>();
            for (String districtName : target.districtNames()) {
                String token = normalizeName(districtName);
                matchesByName.add(districts.stream()
                        .filter(district -> normalizeName(district.name()).contains(token))
                        .map(district -> new RegionCode(target.areaCode(),
                                districtCode(target.areaCode(), district.code()), district.name()))
                        .filter(code -> !code.districtCode().isBlank())
                        .toList());
            }
            int longest = matchesByName.stream().mapToInt(List::size).max().orElse(0);
            for (int index = 0; index < longest; index++) {
                for (List<RegionCode> matches : matchesByName) {
                    if (index < matches.size()) {
                        result.add(matches.get(index));
                    }
                }
            }
        }
        List<RegionCode> distinct = result.stream().distinct().toList();
        if (distinct.isEmpty()) {
            throw new BusinessException(TourApiErrorCode.TOUR_REGION_CODE_NOT_FOUND);
        }
        return distinct;
    }

    /**
     * 장소 주소의 시도·시군구로 혼잡 예측 코드를 찾습니다. 예: {@code 서울 종로구 송현동} → 11/110.
     * 시군구까지 맞출 수 없으면 비어 있는 결과를 돌려 호출자가 추정값으로 대체하게 합니다.
     */
    public Optional<RegionCode> resolveAddress(String address) {
        String[] tokens = normalize(address).split("\\s+");
        if (tokens.length < 2) {
            return Optional.empty();
        }
        String areaCode = sidoCode(tokens[0]);
        if (areaCode == null) {
            return Optional.empty();
        }
        List<LegalDistrict> districts = districtCache.computeIfAbsent(areaCode, tourApi::legalDistricts);
        // "수원시 팔달구"처럼 두 단어인 시군구를 먼저 맞추고, 없으면 첫 단어로 맞춥니다.
        List<String> candidates = tokens.length >= 3
                ? List.of(tokens[1] + tokens[2], tokens[1])
                : List.of(tokens[1]);
        for (String candidate : candidates) {
            String token = normalizeName(candidate);
            Optional<RegionCode> match = districts.stream()
                    .filter(district -> normalizeName(district.name()).equals(token))
                    .map(district -> new RegionCode(areaCode, districtCode(areaCode, district.code()), district.name()))
                    .filter(code -> !code.districtCode().isBlank())
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static String sidoCode(String token) {
        String value = token.replaceAll("(특별자치시|특별자치도|특별시|광역시|도)$", "");
        String exact = SIDO_CODES.get(value);
        if (exact != null) {
            return exact;
        }
        return SIDO_CODES.entrySet().stream()
                .filter(entry -> value.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 앱 지역명 전체와, 그 안의 개별 도시명을 받습니다.
     * 예를 들어 {@code 수원·용인}과 {@code 수원} 모두 수원 시군구로 해석합니다.
     */
    private static List<AreaTarget> targetsFor(String normalized) {
        List<AreaTarget> exact = APP_REGIONS.get(normalized);
        if (exact != null) {
            return exact;
        }
        String token = normalizeName(normalized);
        if (token.isEmpty()) {
            return List.of();
        }
        Map<String, AreaTarget> matches = new LinkedHashMap<>();
        for (List<AreaTarget> targets : APP_REGIONS.values()) {
            for (AreaTarget target : targets) {
                if (target.districtNames().isEmpty()) {
                    if (token.equals(normalizeName(target.label()))) {
                        matches.putIfAbsent(target.areaCode() + ":", target);
                    }
                    continue;
                }
                for (String district : target.districtNames()) {
                    if (token.equals(normalizeName(district))) {
                        matches.putIfAbsent(
                                target.areaCode() + ":" + district,
                                new AreaTarget(target.areaCode(), target.label(), List.of(district)));
                    }
                }
            }
        }
        return List.copyOf(matches.values());
    }

    private static String districtCode(String areaCode, String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 5 && digits.startsWith(areaCode)) {
            return digits.substring(2);
        }
        return digits.length() == 3 ? digits : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeName(String value) {
        return normalize(value).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<AreaTarget>> regions() {
        Map<String, List<AreaTarget>> map = new LinkedHashMap<>();
        put(map, "서울", area("11", "서울"));
        put(map, "인천", area("28", "인천"));
        put(map, "수원·용인", area("41", "경기", "수원", "용인"));
        put(map, "가평·양평", area("41", "경기", "가평", "양평"));
        put(map, "파주·고양", area("41", "경기", "파주", "고양"));
        put(map, "강릉·속초", area("51", "강원", "강릉", "속초"));
        put(map, "춘천·홍천", area("51", "강원", "춘천", "홍천"));
        put(map, "평창·정선", area("51", "강원", "평창", "정선"));
        put(map, "동해·삼척", area("51", "강원", "동해", "삼척"));
        put(map, "대전", area("30", "대전"));
        put(map, "청주", area("43", "충북", "청주"));
        put(map, "충주·제천", area("43", "충북", "충주", "제천"));
        put(map, "태안·보령", area("44", "충남", "태안", "보령"));
        put(map, "공주·부여", area("44", "충남", "공주", "부여"));
        put(map, "전주", area("52", "전북", "전주"));
        put(map, "군산·익산", area("52", "전북", "군산", "익산"));
        map.put("광주·담양", List.of(area("29", "광주"), area("46", "전남", "담양")));
        put(map, "목포·신안", area("46", "전남", "목포", "신안"));
        put(map, "경주", area("47", "경북", "경주"));
        put(map, "대구", area("27", "대구"));
        put(map, "안동", area("47", "경북", "안동"));
        put(map, "포항", area("47", "경북", "포항"));
        put(map, "부산", area("26", "부산"));
        put(map, "울산", area("31", "울산"));
        put(map, "창원", area("48", "경남", "창원"));
        put(map, "통영·거제", area("48", "경남", "통영", "거제"));
        put(map, "남해·사천", area("48", "경남", "남해", "사천"));
        put(map, "여수", area("46", "전남", "여수"));
        put(map, "해남·완도", area("46", "전남", "해남", "완도"));
        put(map, "제주", area("50", "제주"));
        put(map, "제주 성산", area("50", "제주"));
        put(map, "서귀포", area("50", "제주", "서귀포"));
        return Map.copyOf(map);
    }

    private static void put(Map<String, List<AreaTarget>> map, String name, AreaTarget target) {
        map.put(name, List.of(target));
    }

    private static AreaTarget area(String code, String label, String... districts) {
        return new AreaTarget(code, label, List.of(districts));
    }

    private record AreaTarget(
            String areaCode,
            String label,
            List<String> districtNames
    ) {
    }

    public record RegionCode(
            String areaCode,
            String districtCode,
            String label
    ) {
    }
}
