package com.zrlog.plugincore.server.util;

import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(FileUtils.class);

    public static void moveOrCopyFile(String sourceFile, String targetFile, boolean isMove) {
        if (isMove) {
            File dest = new File(targetFile);
            dest.getParentFile().mkdirs();
            File srcFile = new File(sourceFile);
            srcFile.renameTo(dest);
            if (srcFile.exists()) {
                srcFile.delete();
            }
        } else {
            try {
                File f = new File(sourceFile);
                FileInputStream in = new FileInputStream(f);
                new File(targetFile).getParentFile().mkdirs();
                FileOutputStream out = new FileOutputStream(targetFile);
                // 小于1M(大小根据自己的情况而定)的文件直接一次性写入。
                byte[] b = new byte[1024];
                int length = 0; // 出来cnt次后 文件 跳出循环
                while ((length = in.read(b)) != -1) {
                    out.write(b, 0, length);
                }
                // 一定要记得关闭流额。 不然其他程序那个文件无法进行操作
                in.close();
                out.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "copy file " + sourceFile + " to " + targetFile + " error", e);
            }
        }
    }
}
