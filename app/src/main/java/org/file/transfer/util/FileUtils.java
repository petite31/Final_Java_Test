package org.file.transfer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;

public class FileUtils {

    public static byte[] readFileToByteArray(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    public static String getFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            fis.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
