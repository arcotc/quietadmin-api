package uk.co.quietadmin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import uk.co.quietadmin.web.error.ApiError;
import uk.co.quietadmin.web.error.ErrorCode;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ApiError error = new ApiError(
                Instant.now(),
                401,
                ErrorCode.UNAUTHORIZED,
                "Authentication required",
                request.getRequestURI(),
                null
        );

        objectMapper.writeValue(response.getWriter(), error);
    }
}
