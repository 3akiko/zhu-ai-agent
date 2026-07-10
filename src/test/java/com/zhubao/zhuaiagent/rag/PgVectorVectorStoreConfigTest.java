package com.zhubao.zhuaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PgVectorVectorStoreConfigTest {

    @Resource
    private VectorStore vectorStore;

    @Test
    public void testAdd() {
        Document textDoc = new Document("1","Java 线程安全的集合有哪些?\n" +
                "Java ⾥线程安全的集合主要分两类，⼀类是⽼古董级别的同步容器，另⼀类是并发包⾥的⾼性能⽅案。\n" +
                "早期的 Vector 和 Hashtable 确实是线程安全的，⽅法都加了 synchronized，但问题是粒度太粗，同⼀时刻只能⼀个\n" +
                "线程操作，性能差。Collections.synchronizedXxx 包装出来的集合也是类似机制，基本不推荐在新项⽬⾥⽤。\n" +
                "真正扛住⾼并发的是 java.util.concurrent（JUC）包⾥的家伙。⽐如 ConcurrentHashMap，它把数据分成多个\n" +
                "segment（JDK 7）或者⽤ CAS + synchronized 控制桶（JDK 8+），写操作基本不阻塞读，吞吐量能到 HashMap 的\n" +
                "80% 以上。实际开发中，只要涉及并发读写映射表，⼀律上 ConcurrentHashMap。\n" +
                "List ⽅⾯，CopyOnWriteArrayList 适合读多写少场景，⽐如监听器列表。每次修改都会复制整个数组，写代价⼤，但\n" +
                "读完全⽆锁。Set 可以⽤ CopyOnWriteArraySet 或者⽤ ConcurrentHashMap 的 key 来模拟。\n" +
                "Queue 更丰富。BlockingQueue 接⼝下有⼀堆实现，ArrayBlockingQueue 是有界阻塞队列，基于数组；\n" +
                "LinkedBlockingQueue 默认容量 2147483647，适合做⽣产者消费者缓冲；ConcurrentLinkedQueue 是⽆锁链表队\n" +
                "列，⾮阻塞⾼吞吐。\n" +
                "Java 创建线程池有哪些⽅式？\n" +
                "Java ⾥创建线程池，最核⼼的⽅式就是通过 ThreadPoolExecutor 构造函数来搞，其他都是它的封装。", Map.of("source", "user-input"));
 //       vectorStore.add(List.of(textDoc));
    }


}