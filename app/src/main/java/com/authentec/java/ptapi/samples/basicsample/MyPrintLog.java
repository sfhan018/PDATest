package com.authentec.java.ptapi.samples.basicsample;

import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by HKarl on 2017/3/23.
 */

public class MyPrintLog {
    File logFile=null;
    private OutputStream os=null;

    public MyPrintLog() {
        File dic =new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (!dic.exists()) {
            dic.mkdirs();
        } else {
            logFile = new File(dic.getAbsolutePath() + "/fingerLog.txt");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                os=new FileOutputStream(logFile,true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void writelog(String str) {
        try {synchronized (logFile){
            os.write(("\n"+str).getBytes());
        }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            os.close();
            os=null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
