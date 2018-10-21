package com.wyz.lucene.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {
    /**
     * 本地生成文件，并返回文件地址
     * @param file
     * @param filePath
     * @param fileName
     * @return
     * @throws IOException
     */
    public static String uploadFile(byte[] file, String filePath, String fileName) throws IOException {
        File targetFile = new File(filePath);
        if(!targetFile.exists()){
            targetFile.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(filePath+File.separator+fileName);
        out.write(file);
        out.flush();
        out.close();

        return filePath+File.separator+fileName;
    }

}
