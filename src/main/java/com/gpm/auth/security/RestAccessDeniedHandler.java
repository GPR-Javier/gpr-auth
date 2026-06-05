package com.gpm.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        String message = buildMessage(request);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(buildErrorJson(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                message
        ));
    }

    private String buildMessage(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("PUT".equalsIgnoreCase(method) && path.matches(".*/admin/users/\\d+/employee-roles$")) {
            return "Forbidden: missing required authority. Needed one of ROLE_ADMIN, USER_MANAGEMENT:ASSIGN_ROLES, or ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE";
        }

        if ("PUT".equalsIgnoreCase(method) && path.matches(".*/admin/users/\\d+/employee-roles/\\d+/temporary-access$")) {
            return "Forbidden: missing required authority. Needed one of ROLE_ADMIN, USER_MANAGEMENT:ASSIGN_ROLES, or ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE";
        }

        return "Forbidden: you do not have permission to access this resource";
    }

    private String buildErrorJson(int status, String error, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "{" +
                "\"status\":" + status + "," +
                "\"error\":\"" + escapeJson(error) + "\"," +
                "\"message\":\"" + escapeJson(message) + "\"," +
                "\"timestamp\":\"" + timestamp + "\"" +
                "}";
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
