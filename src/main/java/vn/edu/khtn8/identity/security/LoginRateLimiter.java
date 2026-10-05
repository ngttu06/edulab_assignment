package vn.edu.khtn8.identity.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Single-node limiter. A shared limiter is required when deploying multiple instances. */
@Component
public class LoginRateLimiter {
    private record Window(long start,int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    public LoginRateLimiter(){this(Clock.systemUTC());}
    LoginRateLimiter(Clock clock){this.clock=clock;}
    public void check(String address) {
        check(address, "Quá nhiều lần đăng nhập. Vui lòng đợi một phút.");
    }
    public synchronized void check(String address, String message) {
        long now=clock.millis();
        windows.entrySet().removeIf(e->now-e.getValue().start()>=60_000);
        Window window=windows.get(address);
        if(window==null && windows.size()>=10_000) throw limited(message);
        if(window!=null && window.count()>=10) throw limited(message);
        windows.put(address,new Window(window==null?now:window.start(),window==null?1:window.count()+1));
    }
    private static ResponseStatusException limited(String message){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,message);}
}