package com.zhubao.zhuaiagent.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class PathTest {

    @Test
    public void testPath() {
        System.out.println(Path.of(System.getProperty("user.dir"), "./upload"));

    }
}
