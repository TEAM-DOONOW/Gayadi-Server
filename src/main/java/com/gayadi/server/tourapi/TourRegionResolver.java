package com.gayadi.server.tourapi;

import com.gayadi.server.tourapi.model.LegalDistrict;

import com.gayadi.server.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 앱 지역명을 TourAPI 법정동 코드 목록으로 변환합니다. */
@Component
public class TourRegionResolver {

    private static final Map<String, List<AreaTarget>> APP_REGIONS = regions();
    private final TourApiService tourApi;
    private final Map<String, List<LegalDistrict>> districtCache = new ConcurrentHashMap<>();

    public TourRegionResolver(TourApiService tourApi) {
        this.tourApi = tourApi;
    }

    public List<RegionCode> resolve(String regionName) {
        String normalized = normalize(regionName);
        List<AreaTarget> targets = APP_REGIONS.get(normalized);
        if (targets == null) {
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
