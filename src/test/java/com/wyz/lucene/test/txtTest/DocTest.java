package com.wyz.lucene.test.txtTest;

import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DocTest {


    @Test
    public void testDoc() throws IOException {
        String absolutePath = "D:\\lucene\\doc\\风险揭示书-20180902.doc";

        if(absolutePath.endsWith("docx")){
            //doc是新版docx
            XWPFDocument docx = new XWPFDocument(
                    new FileInputStream("D:\\lucene\\doc\\风险揭示书-20180902.docx"));
            XWPFWordExtractor we = new XWPFWordExtractor(docx);
            String text = we.getText();
            System.out.println("文本内容是：\n\n\n\n"+text);
        }else if(absolutePath.endsWith("doc")){
            //老版的doc
            FileInputStream fis = new FileInputStream(new File(absolutePath));
            WordExtractor doc = new WordExtractor(fis);
            String text = doc.getText();
            System.out.println("文本内容是：\n\n\n\n"+text);
        }



    }

}
