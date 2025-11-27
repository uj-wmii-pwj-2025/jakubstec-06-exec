package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(50);
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(50);
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(50);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
        s.shutdown();
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(50);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
        s.shutdown();
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertTrue(s.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> s.submit(new TestRunnable()));
    }

    @Test
    void testShutdownNow() {
        ExecutorService s = MyExecService.newInstance();
        s.submit(() -> doSleep(500));
        doSleep(50);
        s.submit(new TestRunnable());
        s.submit(new TestRunnable());

        List<Runnable> notExecuted = s.shutdownNow();
        
        assertTrue(s.isShutdown());
        assertEquals(2, notExecuted.size());
    }

    @Test
    void testAwaitTermination() throws InterruptedException {
        ExecutorService s = MyExecService.newInstance();
        s.submit(() -> doSleep(50));
        s.shutdown();
        boolean terminated = s.awaitTermination(2, TimeUnit.SECONDS);
        assertTrue(terminated);
        assertTrue(s.isTerminated());
    }

    @Test
    void testInvokeAll() throws InterruptedException, ExecutionException {
        ExecutorService s = MyExecService.newInstance();
        List<Callable<String>> tasks = List.of(
                new StringCallable("A", 10),
                new StringCallable("B", 10)
        );

        List<Future<String>> futures = s.invokeAll(tasks);
        assertEquals(2, futures.size());
        assertEquals("A", futures.get(0).get());
        assertEquals("B", futures.get(1).get());
        s.shutdown();
    }

    @Test
    void testInvokeAllWithTimeout() throws InterruptedException {
        ExecutorService s = MyExecService.newInstance();
        List<Callable<String>> tasks = List.of(
                new StringCallable("Fast", 10),
                new StringCallable("Slow", 500)
        );

        List<Future<String>> futures = s.invokeAll(tasks, 100, TimeUnit.MILLISECONDS);
        
        assertTrue(futures.get(0).isDone());
        assertFalse(futures.get(0).isCancelled());
        
        assertTrue(futures.get(1).isCancelled());
        
        s.shutdownNow();
    }

    @Test
    void testInvokeAny() throws ExecutionException, InterruptedException {
        ExecutorService s = MyExecService.newInstance();
        List<Callable<String>> tasks = new ArrayList<>();
        tasks.add(new StringCallable("Fast", 10));
        tasks.add(new StringCallable("Slow", 500));

        String result = s.invokeAny(tasks);
        assertEquals("Fast", result);
        
        s.shutdownNow();
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class StringCallable implements Callable<String> {
    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}

class TestRunnable implements Runnable {
    volatile boolean wasRun = false;
    @Override
    public void run() {
        wasRun = true;
    }
}