package com.theo.community_api.common.config;

import com.theo.community_api.auth.security.JwtAccessDeniedHandler;
import com.theo.community_api.auth.security.JwtAuthenticationEntryPoint;
import com.theo.community_api.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http
                // 기존 CORS 설정 Bean 사용
                .cors(Customizer.withDefaults())

                // Authorization 헤더로 JWT를 전달하므로 비활성화
                .csrf(csrf -> csrf.disable())

                // 서버 세션에 인증정보를 저장하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth
                        // SSE 비동기 요청의 내부 재디스패치 허용
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        ).permitAll()

                        // CORS 사전 요청 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        // 회원가입과 로그인, 리프레시 토큰 재발급, 로그아웃은 허용
                        .requestMatchers(
                                "/users/signup",
                                "/auth/login",
                                "/auth/reissue",
                                "/auth/logout",
                                "/error",
                                "/images/**",
                                "/actuator/health",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/actuator/loadtest-snapshot"
                        ).permitAll()

                        // 관리자 API
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // 나머지 API는 JWT 인증 필요
                        .anyRequest()
                        .authenticated()
                )

                // JWT 필터 등록
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
