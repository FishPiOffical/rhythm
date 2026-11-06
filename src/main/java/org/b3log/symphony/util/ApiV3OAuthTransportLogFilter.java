/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/** Latke may include a complete malformed form/query in parse errors before OAuth handlers run. */
@Plugin(name="OAuthTransportFilter",category=Node.CATEGORY,elementType=Filter.ELEMENT_TYPE,printObject=true)
public final class ApiV3OAuthTransportLogFilter extends AbstractFilter {
    @PluginFactory public static ApiV3OAuthTransportLogFilter create(){return new ApiV3OAuthTransportLogFilter();}
    /** Never register OAuth endpoints against an old or custom logging configuration that exposes parser inputs. */
    public static void requireInstalled(){
        for(String name:new String[]{"org.b3log.latke.http.Request","org.b3log.latke.http.ServerHandler"}){
            try{
                final var logger=LogManager.getLogger(Class.forName(name));
                if(!(logger instanceof org.apache.logging.log4j.core.Logger core)||!contains(core.getContext().getConfiguration().getLoggerConfig(name).getFilter()))throw new IllegalStateException("OAuth requires updated log4j2.xml and server restart");
            }catch(ClassNotFoundException e){throw new IllegalStateException("Unsupported HTTP transport",e);}
        }
    }
    private static boolean contains(Filter filter){
        if(filter instanceof ApiV3OAuthTransportLogFilter)return true;
        if(filter instanceof CompositeFilter composite)for(Filter child:composite)if(contains(child))return true;
        return false;
    }
    @Override public Result filter(final LogEvent event){
        if(event==null||!("org.b3log.latke.http.Request".equals(event.getLoggerName())||"org.b3log.latke.http.ServerHandler".equals(event.getLoggerName())))return Result.NEUTRAL;
        if(event.getMessage()!=null&&oauth(event.getMessage().getFormattedMessage()))return Result.DENY;
        Throwable cause=event.getThrown();for(int depth=0;cause!=null&&depth<16;depth++,cause=cause.getCause())if(oauth(cause.getMessage()))return Result.DENY;
        return Result.NEUTRAL;
    }
    private static boolean oauth(String value){
        if(value==null)return false;
        // Transport errors can still contain the raw percent-encoded request URI.
        String decoded=value;
        for(int pass=0;pass<3;pass++){
            final String path=decoded.toLowerCase(java.util.Locale.ROOT);
            if(path.contains("/api/v3/me/game-saves/")||path.contains("/api/cloud/")||path.contains("/api/v3/oauth/")||path.contains("/api/v3/auth/")||path.contains("/api/v3/me/security/")||path.contains("/api/v3/me/exports/")
                    ||path.contains("/api/v3/me/identity-applications")||path.contains("/api/v3/review/identity-applications")
                    ||path.contains("/api/v3/me/settings/password")||path.contains("/api/v3/me/settings/phone")
                    ||path.contains("/api/v3/me/settings/email")||path.contains("/api/v3/me/settings/qq")||path.contains("/reset-pwd")||path.contains("/register2"))return true;
            final StringBuilder next=new StringBuilder(decoded.length());boolean changed=false;
            for(int i=0;i<decoded.length();i++){
                if(decoded.charAt(i)=='%'&&i+2<decoded.length()){
                    int hi=Character.digit(decoded.charAt(i+1),16),lo=Character.digit(decoded.charAt(i+2),16);
                    if(hi>=0&&lo>=0){next.append((char)(hi*16+lo));i+=2;changed=true;continue;}
                }
                next.append(decoded.charAt(i));
            }
            if(!changed)return false;decoded=next.toString();
        }
        return false;
    }
}
