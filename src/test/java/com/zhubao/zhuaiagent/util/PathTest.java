package com.zhubao.zhuaiagent.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class PathTest {

    @Test
    public void testPath() {
        System.out.println(Path.of(System.getProperty("user.dir"), "./upload"));
    }

    @Test
    public void print(){
        String s = "Java 中线程安全的集合（即**在多线程环境下无需额外同步即可安全使用的集合类**）主要有以下几类，按 **JDK 版本演进** 和 **实现原理** 分为三类：  \n✅ **传统 synchronized 包装类（已过时，不推荐）**  \n✅ **java.util.concurrent（JUC）包下的高性能并发集合（推荐）**  \n✅ **CopyOnWrite 机制集合（适用于读多写少场景）**  \n\n下面为你系统梳理，并附上**核心原理、适用场景、注意事项及面试要点**：\n\n---\n\n### ✅ 一、`java.util.concurrent`（JUC）包 —— 推荐首选（高性能、分段/无锁/乐观锁）\n\n| 集合类型 | 线程安全实现类 | 核心原理 | 适用场景 | 备注 |\n|----------|----------------|-----------|------------|------|\n| **List** | `CopyOnWriteArrayList` | 写时复制（COW）：每次写操作复制新数组，读不加锁 | ✅ 读多写极少（如监听器列表、配置项）<br>❌ 写频繁或大数据量时内存/CPU开销大 | 迭代器弱一致性（看不到写操作） |\n| **Set** | `CopyOnWriteArraySet` | 底层基于 `CopyOnWriteArrayList` | 同上，去重需求 + 读多写少 | add/remove 基于 list 的线性查找 → O(n) |\n| **Map** | `ConcurrentHashMap` | JDK8+：**CAS + synchronized（锁单个桶）**<br>JDK7：分段锁（Segment） | ✅ 高并发读写通用场景（最常用！）<br>✅ 支持高并发、强一致性（get 不阻塞，put 仅锁桶） | ⚠️ 不支持 `null` key/value（抛 NPE） |\n| **Queue** | `ConcurrentLinkedQueue` | 无锁（Lock-Free），基于 CAS + 单向链表 | ✅ 高吞吐、低延迟的并发队列（非阻塞）<br>❌ 不保证强一致性（size() 是弱一致） | 弱一致性迭代器 |\n| | `BlockingQueue` 实现类：<br>`ArrayBlockingQueue`<br>`LinkedBlockingQueue`<br>`SynchronousQueue`<br>`PriorityBlockingQueue` | 使用 ReentrantLock + Condition 或 LockSupport | ✅ 生产者-消费者模型<br>✅ 需要阻塞/限流/优先级等语义 | `ArrayBlockingQueue` 可选公平锁；`LinkedBlockingQueue` 默认容量 Integer.MAX_VALUE（慎用！） |\n\n> 🔑 **面试高频点**：  \n> - `ConcurrentHashMap` 如何实现线程安全？（答：JDK8 → CAS + synchronized 锁单个 Node；红黑树扩容用 ForwardingNode；get 无锁；key hash 冲突链表转红黑树阈值 8）  \n> - `ConcurrentHashMap` 能否完全替代 `Hashtable`？（✅ 是，且性能更好；`Hashtable` 全表锁太重，已淘汰）  \n> - `CopyOnWriteArrayList` 的迭代器为什么不会抛 `ConcurrentModificationException`？（✅ 因为迭代的是快照副本，与原数组无关）\n\n---\n\n### ✅ 二、Collections.synchronizedXXX（包装器）—— ❌ 仅作兼容，不推荐新代码使用\n\n```java\nList<String> syncList = Collections.synchronizedList(new ArrayList<>());\nMap<String, String> syncMap = Collections.synchronizedMap(new HashMap<>());\n```\n\n- ✅ 原理：所有方法加 `synchronized(this)`，粗粒度锁（整个对象）\n- ❌ 缺点：\n  - 性能差（串行化访问）\n  - **复合操作仍需手动同步**（如 `if (!map.containsKey(key)) map.put(key, val);` 非原子！）\n- 📌 面试提醒：这是“伪线程安全”，常作为反面案例考察你对**原子性 vs 互斥锁**的理解。\n\n---\n\n### ✅ 三、其他线程安全集合（特殊用途）\n\n| 类型 | 类名 | 特点 |\n|------|------|------|\n| **Deque** | `ConcurrentLinkedDeque` | 无锁双端队列（非阻塞） |\n| **SortedSet/Map** | `ConcurrentSkipListSet` / `ConcurrentSkipListMap` | 基于跳表（Skip List），支持排序 + 并发（替代 `TreeSet/TreeMap` 的线程安全版） |\n| **原子容器（非集合，但常被混淆）** | `AtomicIntegerArray`, `AtomicReferenceArray` | 数组元素级原子操作，适合计数、状态标记等，**不是集合接口实现** |\n\n---\n\n### 🚫 常见误区（面试必纠！）\n\n| 错误认知 | 正确理解 |\n|----------|-----------|\n| `\"Vector 和 Hashtable 是线程安全的，所以可以用\"` | ✅ 是线程安全，但**性能极差（全表锁）**，已被 `ConcurrentHashMap` / `CopyOnWriteArrayList` 替代；仅保留用于遗留系统兼容 |\n| `\"synchronizedList 就是线程安全的，不用管了\"` | ❌ 复合操作（如先查后删）仍需 `synchronized` 块包裹，否则存在竞态条件 |\n| `\"ConcurrentHashMap 的 key 可以为 null\"` | ❌ 抛 `NullPointerException`（设计如此，避免歧义） |\n| `\"CopyOnWriteArrayList 适合缓存场景\"` | ⚠️ 仅适合**读远大于写、数据量小、实时性要求不高**的场景（如白名单、事件监听器）；不适合高频更新的缓存（用 Caffeine/Guava Cache） |\n\n---\n\n### ✅ 总结：如何选择？（一句话决策树）\n\n```text\n需要线程安全集合？\n├─ 读多写少 + 小数据量 → CopyOnWriteArrayList / CopyOnWriteArraySet\n├─ 高并发 Map（最常见）→ ConcurrentHashMap（首选！）\n├─ 阻塞队列（生产消费）→ LinkedBlockingQueue / ArrayBlockingQueue\n├─ 无锁高性能队列 → ConcurrentLinkedQueue / ConcurrentLinkedDeque\n├─ 排序 + 并发 → ConcurrentSkipListMap / Set\n└─ 兼容老代码 or 简单场景 → Collections.synchronizedXXX（不推荐新项目）\n```\n\n---\n\n需要我为你：\n- ✅ 深入讲解 `ConcurrentHashMap` 扩容机制（协助迁移、ForwardingNode）？  \n- ✅ 对比 `ConcurrentHashMap` vs `synchronizedMap` 的压测性能差异？  \n- ✅ 提供手写「线程安全的简单计数器」或「带过期的并发缓存」示例？  \n欢迎继续提问！ 😊";
        System.out.println(s);
    }

}
