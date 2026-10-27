package org.b3log.symphony.util;

import org.apache.commons.codec.binary.Base32;
import java.security.SecureRandom;
import java.util.Arrays;

/** Secret generation is independent from the legacy seeded generator and external QR service. */
public final class ApiV3MfaSecrets {
    private static final SecureRandom RANDOM=new SecureRandom();
    private ApiV3MfaSecrets(){ }
    public static String generate(){final byte[] raw=new byte[20];RANDOM.nextBytes(raw);try{return new Base32().encodeAsString(raw);}finally{Arrays.fill(raw,(byte)0);}}
    public static boolean check(String secret,String code,long time){
        if(secret==null||!secret.matches("[A-Z2-7]{32}")||code==null||!code.matches("[0-9]{6}"))return false;
        return MultiFactorAuthenticator.checkCode(secret,Long.parseLong(code),time);
    }
    public static String uri(String actor,String secret){
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}")||secret==null||!secret.matches("[A-Z2-7]{32}"))throw ApiV3WriteInputs.invalid();
        return "otpauth://totp/FishPi%3A"+actor+"?secret="+secret+"&issuer=FishPi&algorithm=SHA1&digits=6&period=30";
    }
}
