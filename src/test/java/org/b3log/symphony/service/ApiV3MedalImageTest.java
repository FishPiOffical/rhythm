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
package org.b3log.symphony.service;
import java.sql.*;import java.util.*;import java.nio.charset.StandardCharsets;
import org.b3log.latke.Latkes;import org.b3log.latke.repository.RepositoryException;import org.b3log.symphony.repository.*;import org.b3log.symphony.util.*;import org.json.*;import org.testng.Assert;
public class ApiV3MedalImageTest {
    static final String SVG="<svg xmlns='http://www.w3.org/2000/svg' width='120' height='28'><defs><linearGradient id='g'><stop offset='0' stop-color='red'/></linearGradient></defs><rect width='120' height='28' fill='url(#g)'/><g transform='translate(1 2)'><text x='4' y='20' style='fill:red;font-size:12px'>测试</text></g><animate attributeName='opacity' values='0;1;0' dur='2s' repeatCount='indefinite'/></svg>";
    static byte[] bytes(String v){return v.getBytes(StandardCharsets.UTF_8);}
    static void status(int expected,Runnable task){try{task.run();throw new AssertionError("expected "+expected);}catch(ApiV3Exception e){Assert.assertEquals(e.status,expected);}}
    static void inject(Object target,String field,Object value)throws Exception{var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable{
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final ApiV3MedalImageQueryService query=new ApiV3MedalImageQueryService();boolean fail;
        Fixture()throws Exception{
            sql("CREATE TABLE u(oId TEXT,userName TEXT,userStatus INT)");sql("CREATE TABLE m(medal_id TEXT,medal_name TEXT,medal_attr TEXT)");sql("CREATE TABLE g(user_id TEXT,medal_id TEXT,expire_time BIGINT,display BOOLEAN)");
            sql("INSERT INTO u VALUES ('1','Owner',0),('2','Reader',0),('3','Pending',2),('4','Restricted',3),('5','Closed',4)");sql("INSERT INTO m VALUES ('7','鱼 & 测试','backcolor=ff0000')");sql("INSERT INTO g VALUES ('1','7',0,0)");
            inject(query,"users",new UserRepository(){public String getName(){return "u";}});inject(query,"grants",new UserMedalRepository(){public String getName(){return "g";}});inject(query,"medals",new MedalRepository(){public String getName(){return "m";}public List<JSONObject> select(String s,Object...a)throws RepositoryException{return rows(s,a);}});
        }
        List<JSONObject> rows(String q,Object...a)throws RepositoryException{if(fail)throw new RepositoryException("fixture");try(var s=db.prepareStatement(q.replace("BINARY g.medal_id","g.medal_id COLLATE BINARY").replace("BINARY m.medal_id","m.medal_id COLLATE BINARY"))){for(int i=0;i<a.length;i++)s.setObject(i+1,a[i]);try(var rs=s.executeQuery()){var out=new ArrayList<JSONObject>();while(rs.next())out.add(new JSONObject().put("medal_id",rs.getString(1)).put("medal_name",rs.getString(2)).put("medal_attr",rs.getString(3)));return out;}}catch(SQLException e){throw new RepositoryException(e);}}
        void sql(String q)throws SQLException{try(var s=db.createStatement()){s.execute(q);}}
        public void close()throws Exception{db.close();}
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            Assert.assertEquals(f.query.definition("1","","7").getString("medal_id"),"7");status(404,()->f.query.definition("2","","7"));status(404,()->f.query.definition("2","Owner","7"));
            status(404,()->f.query.definition("","Owner","7"));status(422,()->f.query.definition("","","7"));
            f.sql("UPDATE g SET display=1");Assert.assertNotNull(f.query.definition("","Owner","7"));Assert.assertEquals(f.query.definition("2","Owner","7").getString("medal_name"),"鱼 & 测试");status(404,()->f.query.definition("3","Owner","7"));
            f.sql("UPDATE g SET expire_time=1");status(404,()->f.query.definition("","Owner","7"));status(404,()->f.query.definition("1","","7"));status(404,()->f.query.definition("2","Owner","7"));
            f.sql("UPDATE g SET expire_time=-1");Assert.assertNotNull(f.query.definition("1","","7"));status(404,()->f.query.definition("2","Owner","7"));
            f.sql("UPDATE g SET expire_time=0");f.sql("UPDATE u SET userStatus=3 WHERE oId='1'");Assert.assertNotNull(f.query.definition("2","Owner","7"));status(404,()->f.query.definition("1","","7"));
            f.sql("UPDATE u SET userStatus=4 WHERE oId='1'");status(404,()->f.query.definition("","Owner","7"));status(404,()->f.query.definition("2","Owner","7"));
        }System.out.println("PASS medal image 1 real SQL owner/public/hidden/expiry/current actor and target statuses");
        try(var f=new Fixture()){status(422,()->f.query.definition("1","bad/name","7"));status(422,()->f.query.definition("1","","bad\nkey"));f.fail=true;status(503,()->f.query.definition("1","","7"));}
        System.out.println("PASS medal image 2 strict identity inputs and explicit repository failure");
        String clean=new String(ApiV3MedalSvg.clean(bytes(SVG)),StandardCharsets.UTF_8);Assert.assertTrue(clean.contains("animate")&&clean.contains("translate(1 2)")&&clean.contains("fill:red")&&clean.contains("测试"));
        String hostile="<?xml-stylesheet href='https://evil/x'?><svg xmlns='http://www.w3.org/2000/svg' onload='evil()'><script>evil()</script><foreignObject><div>BAD_HTML</div></foreignObject><a href='https://evil'><text>link</text></a><image href='https://evil/pixel'/><rect style='fill:url(https://evil/x);stroke:red' filter='url(https://evil)'/><animate attributeName='href' to='javascript:evil()'/></svg>";
        clean=new String(ApiV3MedalSvg.clean(bytes(hostile)),StandardCharsets.UTF_8);for(String s:List.of("evil","script","foreignObject","BAD_HTML","onload","https://evil"))Assert.assertFalse(clean.contains(s),s);Assert.assertTrue(clean.contains("stroke:red"));
        System.out.println("PASS medal image 3 actual SVG sanitizer keeps safe shapes/text/SMIL/styles and strips script HTML navigation and external resources");
        status(503,()->ApiV3MedalSvg.clean(bytes("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg xmlns='http://www.w3.org/2000/svg'>&x;</svg>")));status(503,()->ApiV3MedalSvg.clean(new byte[ApiV3MedalSvg.MAX_BYTES+1]));status(503,()->ApiV3MedalSvg.clean(bytes("<html>bad</html>")));
        System.out.println("PASS medal image 4 DTD external entities wrong document and size rejected without raw echo");
        JSONObject definition=new JSONObject().put("medal_name","鱼 & name").put("medal_attr","url=https%3A%2F%2Ffile.fishpi.cn%2Fa.png%3Fx%3D1%26y%3D2&fontcolor=ffffff");
        String full=ApiV3MedalImageService.parameters(definition,false),mini=ApiV3MedalImageService.parameters(definition,true);Assert.assertTrue(full.contains("txt=%E9%B1%BC+%26+name")&&full.contains("%26y%3D2"));Assert.assertTrue(mini.contains("txt=&"));
        definition.put("medal_attr","url=https://file.fishpi.cn.evil/a");status(503,()->ApiV3MedalImageService.parameters(definition,false));
        System.out.println("PASS medal image 5 renderer settings use server definition only exact asset host and one-time parameter encoding");
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);int[] hits={0};
        server.createContext("/svg",exchange->{hits[0]++;byte[] body=bytes(SVG);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/svg");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/large",exchange->{byte[] body=new byte[ApiV3MedalSvg.MAX_BYTES+1];exchange.sendResponseHeaders(200,body.length);try{exchange.getResponseBody().write(body);}finally{exchange.close();}});server.start();
        try{
            final String base="http://127.0.0.1:"+server.getAddress().getPort();
            ApiV3MedalImageService network=new ApiV3MedalImageService(){protected String rendererURL(){return base+"/svg";}};Assert.assertEquals(new String(network.render("txt=hello"),StandardCharsets.UTF_8),SVG);int before=hits[0];
            ApiV3MedalImageService redirected=new ApiV3MedalImageService(){protected String rendererURL(){return base+"/redirect";}};status(503,()->redirected.render(""));Assert.assertEquals(hits[0],before);
            ApiV3MedalImageService large=new ApiV3MedalImageService(){protected String rendererURL(){return base+"/large";}};status(503,()->large.render(""));
        }finally{server.stop(0);}
        System.out.println("PASS medal image 6 actual HTTP renderer transport bounded body and no redirect follow (loopback fixture)");

        try(var f=new Fixture()){
            ApiV3MedalImageService service=new ApiV3MedalImageService(){protected byte[] render(String p){return bytes(SVG);}};var field=ApiV3MedalImageService.class.getDeclaredField("query");field.setAccessible(true);field.set(service,f.query);Assert.assertTrue(service.image("1","","7",false).length>0);
            ApiV3MedalImageService revoked=new ApiV3MedalImageService(){protected byte[] render(String p){try{f.sql("DELETE FROM g");}catch(Exception e){throw new RuntimeException(e);}return bytes(SVG);}};field.set(revoked,f.query);status(404,()->revoked.image("1","","7",false));
        }System.out.println("PASS medal image 7 actual orchestration rechecks grant after rendering and never serves a revoked cached result");
    }
}
