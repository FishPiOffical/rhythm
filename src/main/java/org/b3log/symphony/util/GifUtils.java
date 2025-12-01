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

/**
import com.gif4j.*;

import java.io.*;

public class GifUtils {
    public static byte[] gifScale(byte[] src, double wQuotiety, double hQuotiety) throws IOException {
        File temp1 = byteArrayToFile(src);
        GifImage srcImage = GifDecoder.decode(temp1);
        if (temp1.exists()) {
            temp1.delete();
        }
        GifImage scaleImg = GifTransformer.scale(srcImage, wQuotiety, hQuotiety, true);
        File dest = File.createTempFile("tempGifFile", null);
        GifEncoder.encode(scaleImg, dest);
        byte[] temp2 = fileToByte(dest);
        if (dest.exists()) {
            dest.delete();
        }
        return temp2;
    }

    public static byte[] fileToByte(File file) {
        byte[] bytes = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            bytes = bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }

    public static File byteArrayToFile(byte[] byteArray) {
        FileOutputStream fos = null;
        try {
            InputStream in = new ByteArrayInputStream(byteArray);
            File file = File.createTempFile("tempByte", null);
            fos = new FileOutputStream(file);
            int len = 0;
            byte[] buf = new byte[1024];
            while ((len = in.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != fos) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}
**/