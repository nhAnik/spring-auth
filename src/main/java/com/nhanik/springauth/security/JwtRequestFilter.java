package com.nhanik.springauth.security;

import com.nhanik.springauth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest,
                                    HttpServletResponse httpServletResponse,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = getJwt(httpServletRequest);
        String username = null;

        // (todo) Remove redundant validation
        if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
            username = jwtUtil.extractUserName(jwt);
            logger.info("Parsed email " + username + " from jwt");
        }

        if (StringUtils.hasText(username) &&
                SecurityContextHolder.getContext().getAuthentication() == null) {
            logger.info("Setting SecurityContext for " + username);
            UserDetails userDetails = userService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            token.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
            SecurityContextHolder.getContext().setAuthentication(token);
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private String getJwt(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
