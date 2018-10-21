package com.wyz.lucene.service;

import java.io.IOException;

public interface FileService {

    void cast2TxtAndIndex(String filePath) throws IOException;

    void sayHello();
}
