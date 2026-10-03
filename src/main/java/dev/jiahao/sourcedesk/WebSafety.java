package dev.jiahao.sourcedesk;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;

@Component
public class WebSafety extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("X-Content-Type-Options","nosniff");
        res.setHeader("Referrer-Policy","no-referrer");
        res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        if(req.getRequestURI().startsWith("/api/")) res.setHeader("Cache-Control","no-store");
        if(!java.util.Set.of("localhost","127.0.0.1","::1","[::1]").contains(req.getServerName())) {
            res.sendError(403); return;
        }
        if(!req.getMethod().equals("GET") && !req.getMethod().equals("HEAD")) {
            String origin=req.getHeader("Origin");
            if("cross-site".equals(req.getHeader("Sec-Fetch-Site")) || (origin!=null && !sameOrigin(origin,req))) {
                res.sendError(403); return;
            }
        }
        chain.doFilter(req,res);
    }
    private boolean sameOrigin(String origin,HttpServletRequest req) {
        try { var uri=URI.create(origin); int port=uri.getPort()==-1?80:uri.getPort(); return uri.getScheme().equals(req.getScheme()) && uri.getHost().equals(req.getServerName()) && port==req.getServerPort(); }
        catch(Exception e) { return false; }
    }
}
