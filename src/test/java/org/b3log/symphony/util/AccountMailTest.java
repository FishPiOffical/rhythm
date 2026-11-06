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

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

/** External SMTP/HTTP calls are replaced; parsing/signing/production branch bodies are real. */
public class AccountMailTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args) {
        check(AccountMailResponses.accepted("sendcloud",200,"{\"result\":true,\"statusCode\":200}"));
        for(String body:new String[]{"{}","{\"result\":false,\"statusCode\":200}","{\"result\":true,\"statusCode\":500}","{\"result\":\"true\",\"statusCode\":200}","{\"result\":true,\"statusCode\":\"200\"}","{\"result\":true,\"result\":false,\"statusCode\":200}","x"})check(!AccountMailResponses.accepted("sendcloud",200,body));
        check(!AccountMailResponses.accepted("sendcloud",500,"{\"result\":true,\"statusCode\":200}"));
        check(AccountMailResponses.accepted("aliyun",200,"{\"RequestId\":\"r\",\"EnvId\":\"e\"}"));
        for(String body:new String[]{"{}","{\"RequestId\":\"r\"}","{\"RequestId\":\"r\",\"EnvId\":\"\"}","{\"RequestId\":\"r\",\"EnvId\":\"e\",\"Code\":\"InvalidReceiver\"}"})check(!AccountMailResponses.accepted("aliyun",200,body));
        check(!AccountMailResponses.accepted("aliyun",200," ".repeat(16385)));
        System.out.println("PASS account mail 1 strict provider acknowledgement, typed JSON, duplicate fields, failure status and bounded malformed response");

        check(Mails.sendAccountBindingCode("new@example.com","123456"));check(MailSender.calls==1);
        MailSender.fail=true;check(!Mails.sendAccountBindingCode("new@example.com","123456"));MailSender.fail=false;
        MailSender.username="";check(!Mails.sendAccountBindingCode("new@example.com","123456"));MailSender.username="user";
        check(!Mails.sendAccountBindingCode("bad","123456"));check(!Mails.sendAccountBindingCode("new@example.com","x"));
        check(!Mails.sendAccountBindingCode("new@example.com\r\nBcc: victim@example.com","123456"));
        System.out.println("PASS account mail 2 actual SMTP branch waits for acceptance, configuration/input rejection and failure sanitization");

        Symphonys.MAIL_CHANNEL="sendcloud";HttpResponse.body="{\"result\":true,\"statusCode\":200}";int closed=HttpResponse.closed;
        check(Mails.sendAccountBindingCode("new@example.com","123456"));check(HttpResponse.closed==closed+1);
        check(HttpRequest.endpoint.equals("https://api.sendcloud.net/apiv2/mail/send"));check(HttpRequest.captured.get("to").equals("new@example.com"));
        HttpResponse.body="{\"result\":false,\"statusCode\":500}";check(!Mails.sendAccountBindingCode("new@example.com","123456"));
        HttpRequest.fail=true;check(!Mails.sendAccountBindingCode("new@example.com","123456"));HttpRequest.fail=false;
        Mails.DOMAIN_CHANNEL.put("example.com","aliyun");HttpResponse.body="{\"RequestId\":\"r\",\"EnvId\":\"e\"}";
        check(Mails.sendAccountBindingCode("new@example.com","123456"));check(HttpRequest.endpoint.equals("https://dm.aliyuncs.com"));
        check(HttpRequest.captured.get("ToAddress").equals("new@example.com"));check(HttpRequest.captured.containsKey("Signature"));
        HttpResponse.body="{\"Code\":\"BadRequest\",\"RequestId\":\"r\"}";check(!Mails.sendAccountBindingCode("new@example.com","123456"));
        for(String line:Mails.LOGGER.lines)check(!line.contains("123456")&&!line.contains("new@example.com")&&!line.contains("private"));
        System.out.println("PASS account mail 3 real SendCloud/Aliyun transport branches, HTTPS, domain routing, signing, response closing and no target/code/body in logs");
    }
}
