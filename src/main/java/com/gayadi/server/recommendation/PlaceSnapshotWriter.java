package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.TourPlaceCandidate;

import java.util.List;
import java.util.Map;

/** 외부 관광 장소 후보를 내부 장소 스냅숏으로 저장하는 계약입니다. */
public interface PlaceSnapshotWriter {

    /** 선택 가능한 TourAPI 후보를 업무 DB의 장소 식별자로 매핑합니다. */
    Map<String, Long> save(List<TourPlaceCandidate> candidates, String destination);
}
