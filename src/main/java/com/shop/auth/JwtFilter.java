package com.shop.auth;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                String phone = jwtUtil.getPhone(token);
                String role  = jwtUtil.getRole(token);
                String userId = jwtUtil.getUserId(token);

                // ── Token Revocation: verify user is still active ──────────────
                // This prevents deactivated users from using old tokens
                Optional<User> userOpt = userRepository.findByPhone(phone);
                if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getActive())) {
                    // User deactivated or deleted — do not authenticate
                    log.warn("SECURITY: Rejected token for inactive/deleted user: {}", phone);
                    chain.doFilter(req, res);
                    return;
                }

                var auth = new UsernamePasswordAuthenticationToken(
                        phone,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                auth.setDetails(userId);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }
}