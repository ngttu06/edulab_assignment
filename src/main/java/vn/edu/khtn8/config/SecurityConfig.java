package vn.edu.khtn8.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.identity.config.JwtProperties;
import vn.edu.khtn8.identity.security.DatabaseUserDetailsService;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            WebAuthenticationEntryPoint entryPoint,
                                            com.fasterxml.jackson.databind.ObjectMapper json,
                                            vn.edu.khtn8.common.config.AppProperties properties,
                                            @org.springframework.beans.factory.annotation.Value("${EDULAB_STORAGE_ALLOWED_HOSTS:c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com}") String storageHosts,
                                            @org.springframework.beans.factory.annotation.Value("${EDULAB_VIDEO_ALLOWED_HOSTS:}") String videoHosts) throws Exception {
        var storageEndpoints=new vn.edu.khtn8.files.StorageEndpointPolicy(storageHosts);
        var videoPolicy=new vn.edu.khtn8.content.video.VideoPolicy(videoHosts,"/js/vendor/playerjs.js");
        String mediaSources=videoPolicy.hosts().stream().map(h->"https://"+h).collect(java.util.stream.Collectors.joining(" "));
        String baseCsp="default-src 'self'; connect-src 'self' "+storageEndpoints.cspSources()+"; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' https://cdn.jsdelivr.net; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
        String communityCsp=baseCsp.replace("img-src 'self' data:;","img-src 'self' data: blob:;");
        String videoCsp="default-src 'self'; connect-src 'self' "+storageEndpoints.cspSources()+" "+mediaSources+" https://www.youtube.com; script-src 'self' https://cdn.jsdelivr.net https://www.youtube.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data: https://i.ytimg.com; media-src 'self' blob: "+mediaSources+"; worker-src 'self' blob:; frame-src https://www.youtube.com https://www.youtube-nocookie.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(properties.getCookie().isSecure()).sameSite("Strict"));
        http
                .csrf(csrfConfigurer -> csrfConfigurer.csrfTokenRepository(csrf))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.FORWARD,DispatcherType.INCLUDE).permitAll()
                        .requestMatchers("/", "/courses", "/courses/*", "/login", "/register", "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/csrf",
                                "/css/**", "/js/**", "/favicon.ico", "/actuator/health").permitAll()
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/ai/support/status").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/ai/support/chat").permitAll()
                        .requestMatchers("/teacher/**", "/api/v1/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/parent/**", "/api/v1/parent/**").hasAnyRole("PARENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(403);
                            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                                response.setContentType("application/json");
                                json.writeValue(response.getOutputStream(), new vn.edu.khtn8.common.web.ApiError(
                                        "FORBIDDEN", "Bạn không có quyền hoặc mã xác thực thao tác đã hết hạn."));
                            } else {
                                response.setContentType("text/plain;charset=UTF-8");
                                response.getWriter().write("Bạn không có quyền thực hiện thao tác này.");
                            }
                        }))
                .headers(headers -> headers
                        .addHeaderWriter((request,response)-> {
                            Object original=request.getAttribute("jakarta.servlet.forward.request_uri");
                            String uri=original instanceof String value ? value : request.getRequestURI();
                            String route=uri.substring(request.getContextPath().length());
                            boolean videoPage=route.matches("/lessons/[0-9]+/video") || route.equals("/teacher/videos") || route.equals("/admin/videos");
                            response.setHeader("Content-Security-Policy",videoPage ? videoCsp : route.equals("/community") || route.startsWith("/community/posts/") ? communityCsp : baseCsp);
                            response.setHeader("Referrer-Policy",videoPage ? "strict-origin-when-cross-origin" : "same-origin");
                            if (route.contains("/video") || route.equals("/teacher/videos")) response.setHeader("Cache-Control","private, no-store");
                            if (route.equals("/admin") || route.startsWith("/admin/")) response.setHeader("Cache-Control","private, no-store");
                            if (route.startsWith("/api/v1/admin/workspace/")) response.setHeader("Cache-Control","private, no-store");
                        })
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frame -> frame.deny()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(DatabaseUserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
