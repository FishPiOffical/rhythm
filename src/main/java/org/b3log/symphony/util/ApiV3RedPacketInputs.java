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

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Confirmed packet contracts and durable allocation, without transport or persistence. */
public final class ApiV3RedPacketInputs {
    private ApiV3RedPacketInputs(){ }
    public record Creation(String type,int money,int count,String message,List<String> recipientIds,boolean riskAccepted,int gesture){
        public JSONObject normalized(){final JSONObject value=new JSONObject().put("type",type).put("money",money).put("count",count).put("message",message).put("recipientIds",new JSONArray(recipientIds));if(riskAccepted)value.put("riskAccepted",true);if(gesture>=0)value.put("gesture",gesture);return value;}
    }
    public record Price(int principal,int pool,int tax,int perShare){ }
    public static Creation prepare(JSONObject input,String actor){
        if(!Set.of("type","money","count","message","recipientIds","riskAccepted","gesture").containsAll(input.keySet())
                ||!(input.opt("type") instanceof String type)||!Set.of("random","average","specify","heartbeat","rockPaperScissors").contains(type)
                ||!input.has("money"))throw ApiV3WriteInputs.invalid();
        final boolean risky=risky(type);
        if(input.has("riskAccepted")&&!(input.opt("riskAccepted") instanceof Boolean)||risky&&!Boolean.TRUE.equals(input.opt("riskAccepted")))throw ApiV3WriteInputs.invalid();
        final int gesture;
        if(type.equals("rockPaperScissors")){if(!input.has("gesture"))throw ApiV3WriteInputs.invalid();gesture=ApiV3WriteInputs.integer(input,"gesture",-1,0,2);}
        else {if(input.has("gesture"))throw ApiV3WriteInputs.invalid();gesture=-1;}
        final int money=ApiV3WriteInputs.integer(input,"money",0,type.equals("rockPaperScissors")?256:32,10240);
        String message=ApiV3WriteInputs.string(input,"message",4096,false);
        message=message.replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fa5,，.。！!?？《》\\s]", "");
        if(message.length()>20||message.codePoints().anyMatch(Character::isISOControl))throw ApiV3WriteInputs.invalid();
        final var recipients=new ArrayList<String>();final int count;
        if(type.equals("specify")){
            if(input.has("count")||!(input.opt("recipientIds") instanceof JSONArray ids)||ids.isEmpty()||ids.length()>100)throw ApiV3WriteInputs.invalid();
            for(Object value:ids){if(!(value instanceof String id)||!id.matches("[1-9][0-9]{0,18}")||id.equals(actor)||recipients.contains(id))throw ApiV3WriteInputs.invalid();recipients.add(id);}
            count=recipients.size();
        }else if(type.equals("rockPaperScissors")){
            if(input.has("recipientIds")||input.has("count")&&ApiV3WriteInputs.integer(input,"count",0,1,1)!=1)throw ApiV3WriteInputs.invalid();count=1;
        }else{
            if(input.has("recipientIds")||!input.has("count"))throw ApiV3WriteInputs.invalid();
            count=ApiV3WriteInputs.integer(input,"count",0,type.equals("heartbeat")?5:1,100);
        }
        return new Creation(type,money,count,message,List.copyOf(recipients),risky,gesture);
    }
    public static Price price(Creation input,boolean premium){
        final int net=premium?input.money():input.money()*(input.type().equals("specify")?99:input.type().equals("rockPaperScissors")?85:97)/100;
        final int multiplier=Set.of("random","heartbeat","rockPaperScissors").contains(input.type())?1:input.count();
        return new Price(Math.multiplyExact(input.money(),multiplier),Math.multiplyExact(net,multiplier),Math.multiplyExact(input.money()-net,multiplier),net);
    }
    public static boolean risky(String type){return Set.of("heartbeat","rockPaperScissors").contains(type);}
    public static JSONObject claim(JSONObject input,String type){
        if(input==null||!Set.of("riskAccepted","gesture").containsAll(input.keySet())
                ||input.has("riskAccepted")&&!(input.opt("riskAccepted") instanceof Boolean)
                ||risky(type)&&!Boolean.TRUE.equals(input.opt("riskAccepted")))throw ApiV3WriteInputs.invalid();
        final JSONObject normalized=new JSONObject();
        if(risky(type))normalized.put("riskAccepted",true);
        if(type.equals("rockPaperScissors")){
            if(!input.has("gesture"))throw ApiV3WriteInputs.invalid();
            normalized.put("gesture",ApiV3WriteInputs.integer(input,"gesture",-1,0,2));
        }else if(input.has("gesture"))throw ApiV3WriteInputs.invalid();
        return normalized;
    }
    public static List<Integer> heartbeat(int pool,int count){return heartbeat(pool,count,ThreadLocalRandom.current());}
    public static List<Integer> heartbeat(int pool,int count,RandomGenerator random){
        if(pool<1||count<5||count>100)throw new IllegalArgumentException();
        final int extra=pool/count+10;final var result=new ArrayList<Integer>();result.add(pool+extra);
        int remaining=extra,left=count-1,zeros=0;
        for(int i=0;i<count-1;i++){
            if(remaining==0){result.add(0);continue;}
            if(left==1){result.add(-remaining);break;}
            int amount=random.nextInt(remaining/left+1);
            if(amount==0){if(zeros<3)zeros++;else amount=1;}
            result.add(-amount);left--;remaining-=amount;
        }
        for(int i=result.size();i>1;i--)Collections.swap(result,i-1,random.nextInt(i));
        if(result.size()!=count||result.stream().mapToInt(Integer::intValue).sum()!=pool)throw new IllegalStateException("packet allocation invalid");
        return List.copyOf(result);
    }
    public static List<Integer> allocate(int money,int count){return allocate(money,count,ThreadLocalRandom.current());}
    public static List<Integer> allocate(int money,int count,RandomGenerator random){
        if(money<0||count<1||count>100)throw new IllegalArgumentException();
        final var values=new ArrayList<Integer>();int zeroCount=2>=count?0:2,zeros=0,remaining=money,left=count;
        for(int i=0;i<count;i++){
            if(remaining==0){values.add(0);continue;}
            if(left==1){values.add(remaining);break;}
            int value=random.nextInt(remaining/left+1);
            if(value==0){if(zeros<zeroCount)zeros++;else value=1;}
            values.add(value);left--;remaining-=value;
        }
        // Same Fisher-Yates shuffle semantics as Collections.shuffle on the old preallocated bucket.
        for(int i=values.size();i>1;i--){final int other=random.nextInt(i);Collections.swap(values,i-1,other);}
        if(values.size()!=count||values.stream().mapToInt(Integer::intValue).sum()!=money)throw new IllegalStateException("packet allocation invalid");
        return List.copyOf(values);
    }
}
