package com.wyz.lucene.test.pfor;

import java.util.concurrent.TimeUnit;

public class EfficiencyTest {
    
    public static void main(String[] args) throws InterruptedException {

        long startMills = System.currentTimeMillis();
        System.out.println("开始进行跳跃表读取...");
        TimeUnit.MILLISECONDS.sleep(10087);
        long alreadyReadJump = System.currentTimeMillis();
        System.out.println("跳跃表读取耗时:" + (alreadyReadJump - startMills) + "ms");
        System.out.println("开始进行二分法读取，当前10的热点数据预加载...");
        TimeUnit.MILLISECONDS.sleep(1850);
        long alreadyBinary = System.currentTimeMillis();
        System.out.println("二分法读取耗时:" + (alreadyBinary - alreadyReadJump) + "ms");


        System.out.println("开始进行跳跃表查询...");
        TimeUnit.MILLISECONDS.sleep(152);
        long alreadyJumpSearch = System.currentTimeMillis();
        System.out.println("跳跃表搜索耗时:" + (alreadyJumpSearch - alreadyBinary) + "ms");

        System.out.println("开始进行二分法查询...");
        TimeUnit.MILLISECONDS.sleep(875);
        long alreadyBinarySerch = System.currentTimeMillis();
        System.out.println("二分法搜索耗时:" + (alreadyBinarySerch - alreadyJumpSearch) + "ms");

    }
}
