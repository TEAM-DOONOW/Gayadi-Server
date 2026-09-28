package com.gayadi.server.route;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class VisitOrderOptimizerTest {
    @Test
    void usesTravelTimeInsteadOfInputOrder() {
        long[][] times = {{0, 20, 1, 50}, {20, 0, 20, 1}, {20, 1, 0, 20}, {50, 20, 20, 0}};
        assertThat(VisitOrderOptimizer.optimize(List.of(0, 1, 2, 3), (a, b) -> times[a][b]))
                .containsExactly(0, 2, 1, 3);
    }

    @Test
    void directedGraphsPreserveEndpointsAndNeverIncreaseCostAndHaveNoImprovingReversal() {
        Random random = new Random(57);
        for (int sample = 0; sample < 100; sample++) {
            long[][] times = new long[7][7];
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    times[i][j] = random.nextInt(100);
                }
            }
            List<Integer> input = List.of(0, 1, 2, 3, 4, 5, 6);
            List<Integer> result = VisitOrderOptimizer.optimize(input, (a, b) -> times[a][b]);
            assertThat(result.getFirst()).isZero();
            assertThat(result.getLast()).isEqualTo(6);
            assertThat(result).containsExactlyInAnyOrderElementsOf(input);
            assertThat(cost(result, times)).isLessThanOrEqualTo(cost(input, times));
            for (int from = 1; from < 5; from++) {
                for (int to = from + 1; to < 6; to++) {
                    List<Integer> reversed = new ArrayList<>(result);
                    Collections.reverse(reversed.subList(from, to + 1));
                    assertThat(cost(reversed, times)).isGreaterThanOrEqualTo(cost(result, times));
                }
            }
        }
    }

    @Test
    void keepsDuplicateVisitsAndShortRoutes() {
        assertThat(VisitOrderOptimizer.optimize(List.of("A", "A", "B", "C"), (a, b) -> 0))
                .containsExactly("A", "A", "B", "C");
        assertThat(VisitOrderOptimizer.optimize(List.of("A", "B"), (a, b) -> 1))
                .containsExactly("A", "B");
    }

    private long cost(List<Integer> route, long[][] times) {
        long result = 0;
        for (int i = 1; i < route.size(); i++) {
            result += times[route.get(i - 1)][route.get(i)];
        }
        return result;
    }
}
