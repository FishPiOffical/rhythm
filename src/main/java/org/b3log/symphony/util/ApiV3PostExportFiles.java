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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.HexFormat;
import java.util.zip.*;

/** 临时文件路径不会写入 HTTP 响应，临时文件在请求结束后清理。 */
public final class ApiV3PostExportFiles implements AutoCloseable {
    public static final long ZIP_LIMIT=32L*1024*1024, SOURCE_LIMIT=256L*1024*1024, ROW_LIMIT=16L*1024*1024;
    public static final int CHUNK_BYTES=48*1024;
    public final Path directory,zip,manifest;
    public ApiV3PostExportFiles()throws IOException {
        directory=Files.createTempDirectory("rhythm-private-export-");
        try{Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException ignored){ }
        zip=directory.resolve("archive.zip");manifest=directory.resolve("manifest.json");
    }
    public static OutputStream bounded(OutputStream target,long limit){return new FilterOutputStream(target){long written;
        @Override public void write(int b)throws IOException{if(++written>limit)throw new IOException("export size budget");out.write(b);}
        @Override public void write(byte[] b,int off,int len)throws IOException{written=Math.addExact(written,len);if(written>limit)throw new IOException("export size budget");out.write(b,off,len);}
    };}
    public static void entry(ZipOutputStream zip,String path,String text,long time)throws IOException{
        if(!path.matches("(articles|comments)/[1-9][0-9]{0,18}(\\.reward)?\\.md"))throw new IOException("invalid export entry");
        var entry=new ZipEntry(path);entry.setTime(time);zip.putNextEntry(entry);zip.write(utf8(text));zip.closeEntry();
    }
    public static byte[] utf8(String text)throws IOException {
        if(text==null)throw new IOException("missing export source");
        for(int i=0;i<text.length();i++){
            final char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IOException("invalid source unicode");}
            else if(Character.isLowSurrogate(c))throw new IOException("invalid source unicode");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
    public static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String sha256(Path path)throws IOException {
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)hash.update(buf,0,n);}return HexFormat.of().formatHex(hash.digest());}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Override public void close()throws IOException {
        IOException error=null;for(Path path:new Path[]{zip,manifest,directory})try{Files.deleteIfExists(path);}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}if(error!=null)throw error;
    }
}
