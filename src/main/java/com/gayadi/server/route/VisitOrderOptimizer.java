package com.gayadi.server.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongBiFunction;

/** 양 끝을 유지하는 열린 경로의 nearest-neighbor + 방향성 2-opt 최적화입니다. */
public final class VisitOrderOptimizer {
    private VisitOrderOptimizer() {
    }

    public static <T> List<T> optimize(List<T> stops, ToLongBiFunction<T, T> duration) {
        if (stops.size() < 4) {
            return List.copyOf(stops);
        }
        List<T> remaining = new ArrayList<>(stops.subList(1, stops.size() - 1));
        List<T> route = new ArrayList<>();
        route.add(stops.getFirst());
        while (!remaining.isEmpty()) {
            int nearest = 0;
            long best = Long.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                long candidate = duration.applyAsLong(route.getLast(), remaining.get(i));
                if (candidate < best) {
                    nearest = i;
                    best = candidate;
                }
            }
            route.add(remaining.remove(nearest));
        }
        route.add(stops.getLast());
        // 기존 순서보다 나빠지는 탐욕해를 채택하지 않습니다.
        if (cost(route, duration) > cost(stops, duration)) {
            route = new ArrayList<>(stops);
        }
        long best = cost(route, duration);
        boolean improved;
        do {
            improved = false;
            for (int from = 1; from < route.size() - 2; from++) {
                for (int to = from + 1; to < route.size() - 1; to++) {
                    Collections.reverse(route.subList(from, to + 1));
                    // 역방향 내부 간선까지 재평가합니다. A→B와 B→A는 다를 수 있습니다.
                    long candidate = cost(route, duration);
                    if (candidate < best) {
                        best = candidate;
                        improved = true;
                    } else {
                        Collections.reverse(route.subList(from, to + 1));
                    }
                }
            }
        } while (improved);
        return List.copyOf(route);
    }

    private static <T> long cost(List<T> route, ToLongBiFunction<T, T> duration) {
        long total = 0;
        for (int i = 1; i < route.size(); i++) {
            total += duration.applyAsLong(route.get(i - 1), route.get(i));
        }
        return total;
    }
}
