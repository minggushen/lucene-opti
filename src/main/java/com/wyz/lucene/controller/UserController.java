package com.wyz.lucene.controller;

import com.wyz.lucene.service.FileService;
import com.wyz.lucene.util.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class UserController {

    @Value("${lucene.path.doc}")
    private String lucenePathDoc;
    @Value("${lucene.path.index}")
    private String lucenePathIndex;
    @Autowired
    private FileService fileService;
    @RequestMapping("/hello")
    public String hello(@RequestParam("file") MultipartFile file) throws Exception {




        //1.保存到本地
        String filePath = FileUtil.uploadFile(file.getBytes(), lucenePathDoc, file.getOriginalFilename());
        //2.解析成纯文本并索引
        fileService.cast2TxtAndIndex(filePath);
        //3.索引
        return "hello";
    }


    @RequestMapping("/hello2")
    @ResponseBody
    public String hello2() throws Exception {
        System.out.println(1111);
        fileService.sayHello();
        System.out.println("main:"+Thread.currentThread().getName());
        return "123";
    }
}
