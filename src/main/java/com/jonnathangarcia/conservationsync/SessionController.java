package com.jonnathangarcia.conservationsync;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/api/session")
    public SessionUser session(Principal principal) {
        return new SessionUser(principal.getName());
    }

    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    public record SessionUser(String username) { }
    public record CsrfResponse(String headerName, String token) { }
}
