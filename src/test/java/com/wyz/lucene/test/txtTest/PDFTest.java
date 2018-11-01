package com.wyz.lucene.test.txtTest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class PDFTest {

    @Test
    public void testPdf() throws IOException {
        PDDocument doc = PDDocument.load(
                new File("D:\\lucene\\pdf\\u-plan-intro.pdf"));
        PDFTextStripper textStripper =new PDFTextStripper();
        String content=textStripper.getText(doc);
        System.out.println("页数：" + doc.getNumberOfPages());
        System.out.println("文本内容：\n\n\n\n" + content);
    }


}
