package uk.ac.open.kmi.iserve.elda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Luca Panziera on 17/07/2014.
 */
public class EldaFilter implements Filter {

    private Logger logger = LoggerFactory.getLogger(EldaFilter.class);
    private RequestDispatcher defaultRequestDispatcher;

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (httpRequest.getMethod().equalsIgnoreCase("GET") && !(httpRequest.getRequestURI().matches("/iserve/discovery.*") || httpRequest.getRequestURI().matches("/iserve/api-docs.*"))) {
            logger.debug(httpRequest.getRequestURI());
            if (httpRequest.getRequestURI().equals("/iserve/")) {
                logger.debug("Redirecting request");
                ((HttpServletResponse) response).sendRedirect(((HttpServletRequest) request).getContextPath() + "/doc/services");
            } else {
                logger.debug("Forwarding request to ELDA");
                defaultRequestDispatcher.forward(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.defaultRequestDispatcher =
                filterConfig.getServletContext().getNamedDispatcher("Elda");
    }
}
