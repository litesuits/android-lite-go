# LiteGo：「迷你」的Android异步并发类库

LiteGo是一款基于Java语言的「异步并发类库」，它的核心是一枚「迷你」并发器，它可以自由地设置同一时段的最大「并发」数量，等待「排队」线程数量，还可以设置「排队策略」和「超载策略」。
LiteGo可以直接投入Runnable、Callable、FutureTask 等类型的实现来运行一个任务，它的核心组件是「SmartExecutor」，它可以用来作为「App」内支持异步并发的唯一组件。
在一个App中「SmartExecutor」可以有多个实例，每个实例都有完全的「独立性」，比如独立的「核心并发」、「排队等待」指标，独立的「运行调度和满载处理」策略，但所有实例「共享一个线程池」。
这种机制既满足不同模块对线程控制和任务调度的独立需求，又共享一个池资源来节省开销，最大程度上节约资源复用线程，帮助提升性能。

---
官网 : [litesuits.com](http://litesuits.com?f=litego)     

QQ群 : [42960650][1]

---

### LiteGo 背景

关于异步、并发的现状和问题

 - 线程的创建代价比较大，尤其在短时间需要大量并发的场景下问题突出，所以Java有了线程池来管理和复用线程。
 - 一般来讲，一个App一个线程池足矣！也不需要自己完全重新实现，充分利用Doug Lea（对java贡献最大的个人）主写的concurrent库。
 - 现在框架众多，有的独立精悍，也有集大成者，建议阅读源码，最好知根知底，很可能在他们有自己的线程池，这个时候如果你不注意管理线程那就雪上加霜咯。

所以，鉴于此我写了这个类库，来统一线程池，明确和控制管理策略。

### LiteGo 理念

 - 清闲时线程不要多持，最好不要超过CPU数量，根据具体应用类型和场景来决策。
 - 瞬间并发不要过多，最好保持在CPU数量左右，或者可以多几个问题并不大。
 - 注意控制排队和满载策略，大量并发瞬间起来的场景下也能轻松应对。

同时并发的线程数量不要过多，最好保持在CPU核数左右，过多了CPU时间片过多的轮转分配造成吞吐量降低，过少了不能充分利用CPU，并发数可以适当比CPU核数多一点没问题。

还有个小小的个人建议，业务上合理调度任务，优化业务逻辑，从自己做起，不胡搞乱搞咯。

### LiteGo 特性
> 
可定义核心并发线程数，即同一时间并发的请求数量。
> 
可定义等待排队线程数，即超出核心并发数后可排队请求数量。
> 
可定义等待队列进入执行状态的策略：先来先执行，后来先执行。
> 
可定义等待队列满载后处理新请求的策略：
> 
 - 抛弃队列中最新的任务
 - 抛弃队列中最旧的任务
 - 抛弃当前新任务
 - 直接执行（阻塞当前线程）
 - 抛出异常（中断当前线程）
 

### LiteGo 使用。 OK，LET IT GO！

初始化：
 
```java

// 智能并发调度控制器：设置[最大并发数]，和[等待队列]大小
SmartExecutor smallExecutor = new SmartExecutor();

// set temporary parameter just for test
// 一下参数设置仅用来测试，具体设置看实际情况。

// number of concurrent threads at the same time, recommended core size is CPU count
// 开发者均衡性能和业务场景，自己调整同一时段的最大并发数量
smallExecutor.setCoreSize(2);

// adjust maximum number of waiting queue size by yourself or based on phone performance
// 开发者均衡性能和业务场景，自己调整最大排队线程数量
smallExecutor.setQueueSize(2);

// 任务数量超出[最大并发数]后，自动进入[等待队列]，等待当前执行任务完成后按策略进入执行状态：后进先执行。
smallExecutor.setSchedulePolicy(SchedulePolicy.LastInFirstRun);

// 后续添加新任务数量超出[等待队列]大小时，执行过载策略：抛弃队列内最旧任务。
smallExecutor.setOverloadPolicy(OverloadPolicy.DiscardOldTaskInQueue);
```
 
上述代码设计了一个可同时并发「2」个线程，并发满载后等待队列可容纳「2」个线程排队，排队队列中后进的任务先执行，等待队列装满后新任务来到将抛弃队列中最老的任务。
 
测试多个线程并发的情况：

```java
// 一次投入 4 个任务
for (int i = 0; i < 4; i++) {
    final int j = i;
    smallExecutor.execute(new Runnable() {
        @Override
        public void run() {
            HttpLog.i(TAG, " TASK " + j + " is running now ----------->");
            SystemClock.sleep(j * 200);
        }
    });
}

// 再投入1个可能需要取消的任务
Future future = smallExecutor.submit(new Runnable() {
    @Override
    public void run() {
        HttpLog.i(TAG, " TASK 4 will be canceled... ------------>");
        SystemClock.sleep(1000);
    }
});

// 合适的时机取消此任务
future.cancel(false);
```
 
 上述代码，一次依次投入 0、1、2、3、4 五个任务，注意4任务是最后投入的，返回一个Future对象。
 
 根据设置，0、1会立即执行，执行满载后2、3进入排队队列，排队满载后独立投入的任务4来到，队列中最老的任务2被移除，队列中为3、4 。
 
 因为4随后被取消执行，所以最后输出：
 
```java
TASK 0 is running now ----------->
TASK 1 is running now ----------->
TASK 3 is running now ----------->
```

 
### LiteGO 基本原理

我们看 SmartExecutor 的几个主要方法：
```java
public Future<?> submit(Runnable task)

public <T> Future<T> submit(Runnable task, T result)

public <T> Future<T> submit(Callable<T> task)

public <T> void submit(RunnableFuture<T> task)

public void execute(final Runnable command)
```

最主要的是 execute 方法，其他几个方法是将任务封装为 FutureTask 投入到 execute 方法执行。因为 FutureTask 本质就是一个 RunnableFuture 对象，兼具 Runnable 和 Future 的特性和功能。

那么重点就是看 execute 方法了：
```java
@Override
public void execute(final Runnable command) {
    if (command == null) {
        return;
    }

    WrappedRunnable scheduler = new WrappedRunnable() {
        @Override
        public Runnable getRealRunnable() {
            return command;
        }

        public Runnable realRunnable;

        @Override
        public void run() {
            try {
                command.run();
            } finally {
                scheduleNext(this);
            }
        }
    };

    boolean callerRun = false;
    synchronized (lock) {
        if (runningList.size() < coreSize) {
            runningList.add(scheduler);
            threadPool.execute(scheduler);
        } else if (waitingList.size() < queueSize) {
            waitingList.addLast(scheduler);
        } else {
            switch (overloadPolicy) {
                case DiscardNewTaskInQueue:
                    waitingList.pollLast();
                    waitingList.addLast(scheduler);
                    break;
                case DiscardOldTaskInQueue:
                    waitingList.pollFirst();
                    waitingList.addLast(scheduler);
                    break;
                case CallerRuns:
                    callerRun = true;
                    break;
                case DiscardCurrentTask:
                    break;
                case ThrowExecption:
                    throw new RuntimeException("Task rejected from lite smart executor. " + command.toString());
                default:
                    break;
            }
        }
        //printThreadPoolInfo();
    }
    if (callerRun) {
        command.run();
    }
}
```

可以看到整个过程简单概括为：
> 
1. 把任务封装为一个类似“链表”的结构体，执行完一个，调度下一个。
2. 加锁防止并发时抢夺资源，判断当前运行任务数量。
3. 当前任务数少于并发最大数量则投入运行，若满载则投入等待队列尾部。
4. 若等待队列未满新任务进入排队，若满则执行满载处理策略。
5. 当一个任务执行完，其尾部通过“链接”的方式调度一个新任务执行。若没有任务，则结束。

其中「加锁」和将任务包装成「链表」是重点。


[1]: http://shang.qq.com/wpa/qunwpa?idkey=19bf15b9c85ec15c62141dd00618f725e2983803cd2b48566fa0e94964ae8370