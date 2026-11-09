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

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** 在有界内存中检查私有 JPEG/PNG 材料，不生成 URL、文件路径、EXIF 日志或公开上传。 */
public final class ApiV3IdentityImage {
    public static final int MAX_BYTES=5*1024*1024;
    private static final java.util.concurrent.Semaphore DECODERS=new java.util.concurrent.Semaphore(2);
    private ApiV3IdentityImage() { }
    public record Image(byte[] bytes,String mediaType,String sha256) { }
    public static Image validate(byte[] input,String mediaType) {
        if(input==null||input.length==0)throw ApiV3WriteInputs.invalid();
        if(input.length>MAX_BYTES)throw new ApiV3Exception(413,"body_too_large","证明图片最多5MiB");
        if(!"image/jpeg".equals(mediaType)&&!"image/png".equals(mediaType))throw new ApiV3Exception(415,"unsupported_media_type","仅支持JPEG或PNG图片");
        final boolean png=input.length>=20&&(input[0]&255)==137&&input[1]==80&&input[2]==78&&input[3]==71
                &&input[4]==13&&input[5]==10&&input[6]==26&&input[7]==10;
        final boolean jpeg=input.length>=4&&(input[0]&255)==255&&(input[1]&255)==216&&(input[input.length-2]&255)==255&&(input[input.length-1]&255)==217;
        if("image/png".equals(mediaType)!=png||"image/jpeg".equals(mediaType)!=jpeg)throw ApiV3WriteInputs.invalid();
        // PNG 必须以固定的 IEND 块结束，拒绝尾随 HTML 或其他文件内容。
        if(png){byte[] end={0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130};
            for(int i=0;i<end.length;i++)if(input[input.length-end.length+i]!=end[i])throw ApiV3WriteInputs.invalid();}
        if(!DECODERS.tryAcquire())throw new ApiV3Exception(503,"temporarily_unavailable","图片解析繁忙，请稍后重试");
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            final var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext())
                throw ApiV3WriteInputs.invalid();
            final var reader=readers.next();
            try {
                final String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!(png?"png".equals(format):"jpeg".equals(format)||"jpg".equals(format)))throw ApiV3WriteInputs.invalid();
                reader.setInput(stream,true,true);
                final int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0)throw ApiV3WriteInputs.invalid();
                // 资源预算耗尽时返回可重试错误，拒绝截断的图片。
                if((long)width*height>50_000_000L)throw new ApiV3Exception(503,"temporarily_unavailable","图片解析资源不足，请稍后重试或使用较小尺寸图片");
                final var parameters=reader.getDefaultReadParam();
                final int sample=Math.max(1,(Math.max(width,height)+255)/256);parameters.setSourceSubsampling(sample,sample,0,0);
                final var decoded = reader.read(0, parameters);
                if (decoded == null)
                    throw ApiV3WriteInputs.invalid();
                decoded.flush();
            } finally {reader.dispose();}
            return new Image(input.clone(),mediaType,hash(input));
        } catch(ApiV3Exception error){throw error;}
        catch(Exception error){throw ApiV3WriteInputs.invalid();}
        finally{DECODERS.release();}
    }
    public static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable");}
    }
}
