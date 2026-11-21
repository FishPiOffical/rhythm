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

import javax.mail.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Real JavaMail Session/MimeMessage and production MailSender; transport never opens a socket. */
public class AccountSmtpTest {
    public static class FakeTransport extends Transport {
        static int sends,closes;static boolean debug,fail;final Session current;
        public FakeTransport(Session session,URLName name){super(session,name);current=session;}
        protected boolean protocolConnect(String host,int port,String user,String password)throws MessagingException {
            debug=current.getDebug();if(fail)throw new MessagingException("FIXTURE_PRIVATE_PROVIDER");return true;
        }
        public void sendMessage(Message value,Address[] recipients)throws MessagingException {
            if(recipients.length!=1)throw new AssertionError();sends++;
            try{if(!value.getContent().toString().contains("123456"))throw new AssertionError();}catch(IOException error){throw new MessagingException("fixture",error);}
        }
        public void close()throws MessagingException {closes++;super.close();}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Class.forName(Mails.class.getName());
        final var sender=MailSender.getInstance();final var field=MailSender.class.getDeclaredField("prop");field.setAccessible(true);
        ((Properties)field.get(null)).put("mail.smtp.class",FakeTransport.class.getName());
        final var logField=MailSender.class.getDeclaredField("LOGGER");logField.setAccessible(true);
        final var logger=(org.apache.logging.log4j.core.Logger)logField.get(null);
        final var configuration=new DefaultConfiguration();configuration.getRootLogger().setLevel(Level.DEBUG);
        logger.getContext().start(configuration);final List<String> lines=new ArrayList<>();
        final var appender=new AbstractAppender("smtp-fixture",null,PatternLayout.createDefaultLayout(),false,Property.EMPTY_ARRAY){public void append(LogEvent event){lines.add(event.getMessage().getFormattedMessage());}};
        appender.start();logger.addAppender(appender);check(logger.isDebugEnabled());
        final var output=new ByteArrayOutputStream();final PrintStream original=System.out;
        try {
            System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));
            check(Mails.sendAccountBindingCode("new@example.com","123456"));
            check(!FakeTransport.debug);check(FakeTransport.sends==1&&FakeTransport.closes==1);check(output.size()==0);
            for(String line:lines)check(!line.contains("123456")&&!line.contains("new@example.com"));
            sender.sendMessage(new String[]{"new@example.com"},"legacy","123456");
            if(!FakeTransport.debug)throw new AssertionError("legacy debug disabled");
            if(FakeTransport.sends!=2)throw new AssertionError("legacy sends "+FakeTransport.sends);
            if(lines.stream().noneMatch(x->x.contains("123456")))throw new AssertionError("legacy log events "+lines.size());
            lines.clear();output.reset();FakeTransport.fail=true;
            check(!Mails.sendAccountBindingCode("new@example.com","123456"));check(!FakeTransport.debug);check(output.size()==0);
            for(String line:lines)check(!line.contains("123456")&&!line.contains("new@example.com")&&!line.contains("FIXTURE_PRIVATE_PROVIDER"));
        }finally{System.setOut(original);logger.removeAppender(appender);appender.stop();}
        System.out.println("PASS real JavaMail and full production Mails/MailSender: account SMTP suppresses debug/body/target despite enabled legacy debug, closes transports and sanitizes errors; legacy behavior retained, no socket opened");
    }
}
