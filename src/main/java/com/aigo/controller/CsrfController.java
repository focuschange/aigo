package com.aigo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF 토큰 쿠키 프라이밍용 엔드포인트.
 *
 * <p>SPA 첫 상호작용이 곧바로 POST 인 경우 {@code XSRF-TOKEN} 쿠키가 아직 내려가지
 * 않아 403 이 발생할 수 있다. 프론트엔드는 첫 POST 전에 한 번 이 엔드포인트를
 * 호출하여 쿠키를 미리 받아 둔다.
 *
 * <p>쿠키 발급 자체는 {@link com.aigo.config.SecurityConfig} 의 materializing filter
 * 가 처리하므로 본 메서드는 단순히 요청이 통과할 경로만 제공한다.
 */
@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<Void> prime() {
        return ResponseEntity.noContent().build();
    }
}
