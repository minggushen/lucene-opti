package com.wyz.lucene.service.impl;

import com.wyz.lucene.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class FileServiceImpl implements FileService {

    @Value("${lucene.path.doc}")
    private String lucenePathDoc;
    @Value("${lucene.path.index}")
    private String lucenePathIndex;

    @Override
    public void cast2TxtAndIndex(String filePath) throws IOException {
//        Path path = Paths.get(filePath);
//        Directory directory = FSDirectory.open(path);
//        Analyzer analyzer = new StandardAnalyzer();
//        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
//        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
//        IndexWriter indexWriter = new IndexWriter(directory, indexWriterConfig);
//        BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("e:/2017_10_17.stderrout.log")));
//        String content = "";
//        while ((content = bufferedReader.readLine())!=null){
//            System.out.println(content);
//            Document document = new Document();
//            document.add(new TextField("logs", content, Field.Store.YES));
//            indexWriter.addDocument(document);
//        }
//
//        indexWriter.close();


//        new MyBinarySearchReader(cfsDir, segment, fieldInfos);

    }

    @Override
    @Async
    public void sayHello() {
        System.out.println("sdadasdsadsadasd");
        System.out.println("not main:"+Thread.currentThread().getName());
    }
}
