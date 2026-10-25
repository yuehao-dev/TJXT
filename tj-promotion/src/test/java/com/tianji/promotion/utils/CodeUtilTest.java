package com.tianji.promotion.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeUtilTest {

    @Test
    void testCodeUtil(){
        for (int i = 4000; i < 10000; i++) {
            String code = CodeUtil.generateCode(i, 1000);
            System.out.println("code: " + code);

            long serialNum = CodeUtil.parseCode(code);
            System.out.println("serialNum: " + serialNum);
            System.out.println("-----------------");
        }

    }

}