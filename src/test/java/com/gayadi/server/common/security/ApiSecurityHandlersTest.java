package com.gayadi.server.common.security;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.response.ApiErrorResponseFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class ApiSecurityHandlersTest {

    private final ApiSecurityErrorWriter writer = new ApiSecurityErrorWriter(
            new ApiErrorResponseFactory(),
            new JsonSupport(new ObjectMapper()));

    @Test
    void authenticationFailureUsesCommon401Contract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/current");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(writer).commence(
                request, response, new BadCredentialsException("raw authentication detail"));

        assertResponse(response, 401, "UNAUTHENTICATED", "/api/v1/users/current");
        Assertions.assertThat(response.getContentAsString()).doesNotContain("raw authentication detail");
    }

    @Test
    void accessDeniedUsesCommon403Contract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/admin/notices/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(writer).handle(
                request, response, new AccessDeniedException("raw authorization detail"));

        assertResponse(response, 403, "ACCESS_DENIED", "/api/v1/admin/notices/1");
        Assertions.assertThat(response.getContentAsString()).doesNotContain("raw authorization detail");
    }

    private void assertResponse(
            MockHttpServletResponse response,
            int status,
            String code,
            String path) throws Exception {
        Assertions.assertThat(response.getStatus()).isEqualTo(status);
        Assertions.assertThat(response.getContentType()).startsWith("application/json");
        Assertions.assertThat(response.getContentAsString())
                .contains("\"status\":" + status)
                .contains("\"code\":\"" + code + "\"")
                .contains("\"path\":\"" + path + "\"")
                .contains("\"traceId\":")
                .contains("\"details\":null")
                .doesNotContain("stackTrace")
                .doesNotContain("exception");
    }
}
