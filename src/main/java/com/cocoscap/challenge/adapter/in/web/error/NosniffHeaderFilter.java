package com.cocoscap.challenge.adapter.in.web.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@code X-Content-Type-Options: nosniff} to every response (ADR-007).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NosniffHeaderFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Content-Type-Options";
    static final String HEADER_VALUE = "nosniff";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(HEADER_NAME, HEADER_VALUE);
        filterChain.doFilter(request, response);
    }
}
