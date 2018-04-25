/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2018.                         (c) 2018.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 4/20/12 - 11:07 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import ca.nrc.cadc.net.NetUtil;
import org.apache.log4j.Logger;

import ca.nrc.cadc.util.ArrayUtil;
import ca.nrc.cadc.util.StringUtil;


/**
 * Implementation of a Principal Extractor from an HttpServletRequest.
 */
public class ServletPrincipalExtractor implements PrincipalExtractor
{
    private static final Logger log = Logger.getLogger(ServletPrincipalExtractor.class);
    
    public static final String CERT_REQUEST_ATTRIBUTE = "javax.servlet.request.X509Certificate";
    private final HttpServletRequest request;

    private X509CertificateChain chain;
    private DelegationToken token;
    private List<SSOCookieCredential> cookieCredentialList;
    private Set<Principal> cookiePrincipals = new HashSet<>(); // identities extracted from cookie
    private Set<Principal> principals = new HashSet<>();

    private ServletPrincipalExtractor()
    {
        this.request = null;
    }

    /**
     * Constructor to create Principals from the given Servlet Request.
     *
     * @param req       The HTTP Request.
     */
    public ServletPrincipalExtractor(final HttpServletRequest req)
    {
        this.request = req;
        X509Certificate[] ca = (X509Certificate[])
            request.getAttribute(CERT_REQUEST_ATTRIBUTE);

        if (!ArrayUtil.isEmpty(ca)) {
            chain = new X509CertificateChain(Arrays.asList(ca));
            if (chain != null) {
                principals.add(chain.getPrincipal());
            }
        }

        // add user if they have a valid delegation token
        String tokenValue = request.getHeader(AuthenticationUtil.AUTH_HEADER);
        if ( StringUtil.hasText(tokenValue) )
        {

            try
            {
                this.token = DelegationToken.parse(tokenValue, 
                        request.getRequestURI());
            }
            catch (InvalidDelegationTokenException ex) 
            {
                log.debug("invalid DelegationToken: " + tokenValue, ex);
                throw new AccessControlException("invalid delegation token");
            }
            catch(RuntimeException ex)
            {
                log.debug("invalid DelegationToken: " + tokenValue, ex);
                throw new AccessControlException("invalid delegation token");
            }
            finally { }
        }

        // add HttpPrincipal
        final String httpUser = request.getRemoteUser();
        if (StringUtil.hasText(httpUser)) // user from HTTP AUTH
            principals.add(new HttpPrincipal(httpUser));
        else if (token != null) // user from token
            principals.add(token.getUser());
        
        Cookie[] cookies = request.getCookies();
        if (cookies == null || ArrayUtil.isEmpty(cookies))
            return;
        
        for (Cookie ssoCookie : cookies)
        {
            if (SSOCookieManager.DEFAULT_SSO_COOKIE_NAME.equals(
                    ssoCookie.getName())
                    && StringUtil.hasText(ssoCookie.getValue()))
            {
                SSOCookieManager ssoCookieManager = new SSOCookieManager();
                try
                {
                    DelegationToken cookieToken = ssoCookieManager.parse(
                        ssoCookie.getValue());

                    cookiePrincipals = cookieToken.getIdentityPrincipals();
                    principals.addAll(cookiePrincipals);

                    cookieCredentialList = ssoCookieManager.getSSOCookieCredentials(ssoCookie.getValue(),
                                                NetUtil.getDomainName(request.getServerName()));
                } 
                catch (IOException e)
                {
                    log.info("Cannot use SSO Cookie. Reason: " 
                            + e.getMessage());
                } 
                catch (InvalidDelegationTokenException e)
                {
                    log.info("Cannot use SSO Cookie. Reason: " 
                            + e.getMessage());
                }
                
            }
        }
        
    }

    /**
     * Obtain a Collection of Principals from this extractor.  This should be
     * immutable.
     *
     * @return Collection of Principal instances, or empty Collection.
     *         Never null.
     */
    @Override
    public Set<Principal> getPrincipals()
    {
        return principals;
    }

    public X509CertificateChain getCertificateChain()
    {
        return chain;
    }

    public DelegationToken getDelegationToken() 
    {
        return token;
    }
    
    public List<SSOCookieCredential> getSSOCookieCredentials() {
        return cookieCredentialList;
    }

}
