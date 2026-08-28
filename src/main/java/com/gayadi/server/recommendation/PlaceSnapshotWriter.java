package com.gayadi.server.recommendation;

import java.util.List;
import java.util.Map;

public interface PlaceSnapshotWriter {

    /** 선택 가능한 TourAPI 후보를 업무 DB의 장소 식별자로 매핑합니다. */
    Map<String, Long> save(List<TourPlaceCandidate> candidates, String destination);
}
