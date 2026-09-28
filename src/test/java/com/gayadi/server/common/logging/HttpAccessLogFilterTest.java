package com.gayadi.server.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAccessLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void clearMdc() {
        MDC.clear();
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsCompletedRequestAtInfoWithTraceId() throws Exception {
        HttpAccessLogFilter filter = new HttpAccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachAppender();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(MDC.get(RequestTrace.MDC_KEY)).isNotBlank();
            response.setStatus(204);
        });

        String traceId = response.getHeader(RequestTrace.RESPONSE_HEADER);
        assertThat(traceId).isNotBlank();
        assertThatCodeIsUuid(traceId);
        assertThat(MDC.get(RequestTrace.MDC_KEY)).isNull();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("method=GET", "path=/api/v1/places", "status=204", "durationMs=");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestTrace.MDC_KEY, traceId);
        });
    }

    @Test
    void logsUnhandledFailureAsInternalServerError() {
        HttpAccessLogFilter filter = new HttpAccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/trips");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachAppender();

        assertThatThrownBy(() -> filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> {
                    throw new IllegalStateException("test failure");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(appender.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("method=POST", "path=/api/v1/trips", "status=500"));
        assertThat(MDC.get(RequestTrace.MDC_KEY)).isNull();
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void assertThatCodeIsUuid(String traceId) {
        assertThat(UUID.fromString(traceId)).isNotNull();
    }
}
