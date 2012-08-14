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

import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

final class MediaTypeAlteringHttpServletRequestWrapper extends HttpServletRequestWrapper
{
    private final static String ACCEPT_HEADER = "accept";
    private final static String ACCEPT_HEADER_IGNORED = "ignored";
    
    private final String acceptHeader;

    public MediaTypeAlteringHttpServletRequestWrapper(HttpServletRequest request, String acceptHeader)
    {
        super(request);
        this.acceptHeader = acceptHeader;
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        if(ACCEPT_HEADER.equalsIgnoreCase(name))
        {
            Hashtable<String, String> fakeAcceptHeader = new Hashtable<String, String>();
            fakeAcceptHeader.put(this.acceptHeader, ACCEPT_HEADER_IGNORED);
            return fakeAcceptHeader.keys();
        }

        return super.getHeaders(name);
    }

    @Override
    public String getHeader(String name)
    {
        if(ACCEPT_HEADER.equalsIgnoreCase(name))
        {
            return this.acceptHeader;
        }

        return super.getHeader(name);
    }
}