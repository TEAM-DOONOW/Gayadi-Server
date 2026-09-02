package com.gayadi.server.auth;

import com.gayadi.server.auth.model.GoogleIdentity;

/** Google ID 토큰의 서명·만료·issuer·audience를 검증합니다. */
public interface GoogleIdTokenClient {

    boolean isConfigured();

    GoogleIdentity verify(String idToken);
}
