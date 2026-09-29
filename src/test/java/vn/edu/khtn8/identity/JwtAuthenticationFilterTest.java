package vn.edu.khtn8.identity;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void missingIdentityClaimsStayAnonymous() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(DatabaseUserDetailsService.class);
        when(jwt.parse("token")).thenReturn(mock(Claims.class));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_COOKIE,"token"));
        new JwtAuthenticationFilter(jwt,users).doFilter(request,new MockHttpServletResponse(),(req,res) -> { });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
    }
    @Test void deletedUserDoesNotCrashRequest() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(DatabaseUserDetailsService.class);
        var claims = mock(Claims.class);
        when(jwt.parse("token")).thenReturn(claims);
        when(claims.get("uid",Long.class)).thenReturn(1L);
        when(claims.get("av",Integer.class)).thenReturn(0);
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis()+60000));
        when(users.loadById(1L)).thenThrow(new UsernameNotFoundException("missing"));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_COOKIE,"token"));
        new JwtAuthenticationFilter(jwt,users).doFilter(request,new MockHttpServletResponse(),(req,res) -> { });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void authVersionChangeRevokesOldToken() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(DatabaseUserDetailsService.class);
        var claims = mock(Claims.class);
        when(jwt.parse("token")).thenReturn(claims);
        when(claims.get("uid",Long.class)).thenReturn(1L);
        when(claims.get("av",Integer.class)).thenReturn(0);
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis()+60000));
        when(users.loadById(1L)).thenReturn(new AuthenticatedUser(1L,"student","Student","hash",true,1,List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_COOKIE,"token"));
        new JwtAuthenticationFilter(jwt,users).doFilter(request,new MockHttpServletResponse(),(req,res) -> { });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
