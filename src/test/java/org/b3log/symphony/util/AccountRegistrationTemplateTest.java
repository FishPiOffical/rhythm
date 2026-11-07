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

import freemarker.template.*;
import org.testng.Assert;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class AccountRegistrationTemplateTest {
    public static void main(String[] args)throws Exception {
        final var config=new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        for(String skin:List.of("pc","mobile")){
            final String source=Files.readString(Path.of("src/main/resources/skins/classic/"+skin+"/verify/register2.ftl"));
            new Template("registration-"+skin,new StringReader(source),config); // Full source syntax, no application macro/context simulation.
            final var matcher=Pattern.compile("<input id=\"registerCode2\"[^>]*>").matcher(source);Assert.assertTrue(matcher.find());
            final var field=new Template("registration-proof-"+skin,new StringReader(matcher.group()),config);
            for(String proof:List.of("123456","\"/><img src=x onerror=alert(1)>")){
                final var writer=new StringWriter();field.process(Map.of("code",proof),writer);String rendered=writer.toString();
                Assert.assertFalse(rendered.contains("<img"));Assert.assertFalse(rendered.contains("value=\"\"/>"));if(proof.equals("123456"))Assert.assertTrue(rendered.contains("value=\"123456\""));else Assert.assertTrue(rendered.contains("&quot;"));
            }
            final var empty=new StringWriter();field.process(Map.of(),empty);Assert.assertTrue(empty.toString().contains("value=\"\""));
        }
        System.out.println("PASS full PC/mobile registration FTL syntax and actual hidden-proof fragment render six-digit values, escaped hostile values and an empty missing proof; full application macros/page context remain unexecuted");
    }
}
