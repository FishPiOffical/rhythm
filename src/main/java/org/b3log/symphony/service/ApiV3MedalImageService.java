package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3MedalSvg;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Only the configured renderer is contacted. No HTTP caller-supplied render settings. */
@Service
public class ApiV3MedalImageService {
    @Inject private ApiV3MedalImageQueryService query;
    private static final Semaphore RENDERS=new Semaphore(4);
    public byte[] image(String actor,String name,String medalId,boolean mini){
        final JSONObject before=query.definition(actor,name,medalId);
        final String parameters=parameters(before,mini);
        if(!RENDERS.tryAcquire())throw ApiV3MedalSvg.bad();
        try{
            byte[] svg=ApiV3MedalSvg.clean(render(parameters));
            // No render cache is allowed to bypass a current grant, account or definition check.
            if(!before.similar(query.definition(actor,name,medalId)))throw ApiV3MedalSvg.bad();
            return svg;
        }finally{RENDERS.release();}
    }
    protected String rendererURL(){return Symphonys.get("gen.metal.url");}
    protected byte[] render(String parameters){
        try{
            URI target=URI.create(rendererURL()+"?"+parameters);
            if(!Set.of("http","https").contains(target.getScheme())||target.getHost()==null||target.getRawUserInfo()!=null||target.getRawFragment()!=null)throw ApiV3MedalSvg.bad();
            HttpURLConnection connection=(HttpURLConnection)target.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(1000);
            try{
                if(connection.getResponseCode()!=200)throw ApiV3MedalSvg.bad();
                final long deadline=System.nanoTime()+5_000_000_000L;
                try(var body=connection.getInputStream();var output=new java.io.ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int read;
                    while((read=body.read(buffer))!=-1){if(output.size()+read>ApiV3MedalSvg.MAX_BYTES||System.nanoTime()>deadline)throw ApiV3MedalSvg.bad();output.write(buffer,0,read);}
                    return output.toByteArray();
                }
            }finally{connection.disconnect();}
        }catch(Exception error){throw ApiV3MedalSvg.bad();}
    }
    public static String parameters(JSONObject definition,boolean mini){
        String attr=definition.optString("medal_attr"),title=definition.optString("medal_name");
        if(attr.length()>8192||title.length()>128||title.codePoints().anyMatch(Character::isISOControl))throw ApiV3MedalSvg.bad();
        Map<String,String> args=new LinkedHashMap<>();args.put("ver","0.1");args.put("scale","0.79");args.put("txt",mini?"":title);
        Set<String> allowed=Set.of("url","backcolor","fontcolor","shadow","anime","way","fontway","size","border","barlen","font","fontsize","barradius");
        for(String part:attr.split("&")){
            String[] pair=part.split("=",2);if(pair.length!=2||!allowed.contains(pair[0]))continue;
            String key=pair[0],value=pair[1];
            // Stored attributes can contain an already encoded value; decode once, then encode once below.
            try{value=URLDecoder.decode(value.replace("+","%2B"),StandardCharsets.UTF_8);}catch(IllegalArgumentException e){throw ApiV3MedalSvg.bad();}
            if(value.length()>4096||value.codePoints().anyMatch(Character::isISOControl))throw ApiV3MedalSvg.bad();
            if(key.equals("url")&&!value.isEmpty()){
                try{URI u=URI.create(value);if(!"https".equals(u.getScheme())||!"file.fishpi.cn".equalsIgnoreCase(u.getHost())||u.getRawUserInfo()!=null||u.getPort()!=-1||u.getRawFragment()!=null||value.indexOf('\\')>=0)throw ApiV3MedalSvg.bad();}catch(IllegalArgumentException e){throw ApiV3MedalSvg.bad();}
            }else if(Set.of("size","border","fontsize","barradius").contains(key)&&!value.isEmpty()){
                if(!value.matches("[0-9]{1,4}")||Integer.parseInt(value)>2048)throw ApiV3MedalSvg.bad();
            }else if(!key.equals("url")&&!value.matches("[A-Za-z0-9#.,_ /+=-]{0,4096}"))throw ApiV3MedalSvg.bad();
            args.put(key,value);
        }
        return args.entrySet().stream().map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
}
