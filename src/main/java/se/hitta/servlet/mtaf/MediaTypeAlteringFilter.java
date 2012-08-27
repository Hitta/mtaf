/*
 * Copyright 2012 Hittapunktse AB (http://www.hitta.se/)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.hitta.servlet.mtaf;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.inject.Singleton;
import com.sun.jersey.core.header.AcceptableMediaType;
import com.sun.jersey.core.header.MediaTypes;
import com.sun.jersey.core.header.reader.HttpHeaderReader;

/**
 * This filter will alter the request accept header to <b>application/javascript</b>
 * if the client sends a compatible request accept header, and there is a <b>callback</b> parameter present.<br />
 * 
 * If applying this filter, you will be able to assure sanity for your '@Produces' annotations, e.g. <br>
 * <ul>
 * <li>a resource not annotated with @Produces({"application/javascript"...}) will render a '406 Not Acceptable' if requested with a callback and an acceptable media-type.</li>
 * <li>a resource annotated with @Produces({"application/javascript"...}) will only accept JSONP requests when a valid callback is present.</li>
 * </ul>
 */
@Singleton
public final class MediaTypeAlteringFilter implements Filter
{
    private final static String CALLBACK_PARAMETER_NAME = "callback";
    private final static String VARY_HEADER = "Vary";
    private final static MediaType APPLICATION_JAVASCRIPT_TYPE = new MediaType("application", "javascript");
    private final static String APPLICATION_JAVASCRIPT = "application/javascript";
    
    private final static Logger log = LoggerFactory.getLogger(MediaTypeAlteringFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
        List<AcceptableMediaType> acceptables = acceptableMediaTypes(httpRequest);
        
        Optional<String> callback = Optional.fromNullable(httpRequest.getParameter(CALLBACK_PARAMETER_NAME));

        if(jsonpAccepted(httpRequest, acceptables))
        {
            if(callback.isPresent())
            {
                log.info(CALLBACK_PARAMETER_NAME + " parameter and compatible accept header detected, wrapping request to alter accept header (to 'application/javascript')");
                httpRequest = new MediaTypeAlteringHttpServletRequestWrapper(httpRequest, APPLICATION_JAVASCRIPT);
            }
            else
            {
                MediaType preferredMediaType = getPreferredMediaType(acceptables);
                
                //if 'application/javascript' is preferred by the client, but no callback parameter is present,
                //strip this media-type from the request before continuing
                if(sameType(preferredMediaType, APPLICATION_JAVASCRIPT_TYPE))
                {
                    //ok, actually only strip if there is a fallback
                    if(acceptables.size() > 1)
                    {
                        acceptables.remove(preferredMediaType);
                        String accept = Joiner.on(',').join(acceptables);
                        httpRequest = new MediaTypeAlteringHttpServletRequestWrapper(httpRequest, accept);
                    }
                    else
                    {
                        formatErrorResponse(servletResponse, Status.BAD_REQUEST, "jsonp callback parameter missing"); return;                    
                    }
                }
                
            }
        }
        else if(callback.isPresent())
        {
            formatErrorResponse(servletResponse, Status.BAD_REQUEST, "jsonp callback detected, but no 'application/javascript' compatible media type"); return;
        }

        chain.doFilter(httpRequest, servletResponse);
    }
    
    private static void formatErrorResponse(ServletResponse servletResponse, Status status, String message)
    {
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        response.setStatus(status.getStatusCode());
        response.setContentType(MediaType.TEXT_PLAIN);
        response.setHeader(VARY_HEADER, "Accept");
        
        try
        {
            OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
            writer.write(message);
            writer.flush();
        }
        catch(Exception e){}
    }

    /**
     * like MediaType.equals but without 'parameters' (typically quality factor)
     * @param first
     * @param second
     * @return true if 'first' and 'second' matches
     */
    private static boolean sameType(MediaType first, MediaType second)
    {
        return  first.getType().equalsIgnoreCase(second.getType()) && first.getSubtype().equalsIgnoreCase(second.getSubtype());
    }
    
    /**
     * @param request
     * @param acceptables
     * @return true if the client accepts a JSONP result
     */
    private static boolean jsonpAccepted(HttpServletRequest request, List<AcceptableMediaType> acceptables)
    {
        for(MediaType acceptedType : acceptables)
        {
            if(sameType(acceptedType, MediaType.WILDCARD_TYPE) || acceptedType.isCompatible(APPLICATION_JAVASCRIPT_TYPE))
            {
                return true;
            }
        }
        return false;
    }
    
    private static MediaType getPreferredMediaType(List<AcceptableMediaType> acceptableMediaTypes)
    {
        int quality = 0;
        MediaType preferredMediaType = null;
        
        for(AcceptableMediaType acceptableMediaType : acceptableMediaTypes)
        {
            if(acceptableMediaType.getQuality() > quality)
            {
                preferredMediaType = acceptableMediaType;
                quality = acceptableMediaType.getQuality();
            }
        }

        return preferredMediaType;
    }
    
    private static List<AcceptableMediaType> acceptableMediaTypes(HttpServletRequest request)
    {
        final String accept = request.getHeader(HttpHeaders.ACCEPT);

        if(accept == null || accept.length() == 0)
        {
            return MediaTypes.GENERAL_ACCEPT_MEDIA_TYPE_LIST;
        }
        try
        {
            return HttpHeaderReader.readAcceptMediaType(accept);
        }
        catch(java.text.ParseException e)
        {
            return new ArrayList<AcceptableMediaType>(); // do not bother to throw an exception
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        //NOP
    }

    @Override
    public void destroy()
    {
        //NOP
    }
}
