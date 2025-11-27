package uj.wmii.pwj.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {

    private final BlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();
    private final Thread runner;
    private volatile boolean stopped = false;
    private volatile boolean dead = false;

    private MyExecService() {
        runner = new Thread(this::runLoop);
        runner.start();
    }

    static MyExecService newInstance() {
        return new MyExecService();
    }

    private void runLoop() {
        try {
            while (!dead) {
                if (stopped && actions.isEmpty()) {
                    dead = true;
                    break;
                }
                try {
                    Runnable r = actions.poll(50, TimeUnit.MILLISECONDS);
                    if (r != null) {
                        r.run();
                    }
                } catch (InterruptedException e) {
                    if (stopped) {
                        dead = true;
                        break;
                    }
                }
            }
        } finally {
            dead = true;
        }
    }

    @Override
    public void shutdown() {
        stopped = true;
        runner.interrupt();
    }

    @Override
    public List<Runnable> shutdownNow() {
        stopped = true;
        runner.interrupt();
        List<Runnable> remaining = new ArrayList<>();
        actions.drainTo(remaining);
        return remaining;
    }

    @Override
    public boolean isShutdown() {
        return stopped;
    }

    @Override
    public boolean isTerminated() {
        return dead;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        runner.join(unit.toMillis(timeout));
        return dead;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        if (stopped) throw new RejectedExecutionException();
        FutureTask<T> ft = new FutureTask<>(task);
        addToQueue(ft);
        return ft;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        if (stopped) throw new RejectedExecutionException();
        FutureTask<T> ft = new FutureTask<>(task, result);
        addToQueue(ft);
        return ft;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        if (stopped) throw new RejectedExecutionException();
        FutureTask<?> ft = new FutureTask<>(task, null);
        addToQueue(ft);
        return ft;
    }

    private void addToQueue(Runnable r) {
        try {
            actions.put(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException(e);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) throw new NullPointerException();
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> c : tasks) {
            futures.add(submit(c));
        }
        for (Future<T> f : futures) {
            if (!f.isDone()) {
                try {
                    f.get();
                } catch (ExecutionException | CancellationException ignored) {
                }
            }
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null) throw new NullPointerException();
        long end = System.nanoTime() + unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<>();

        for (Callable<T> c : tasks) {
            futures.add(submit(c));
        }

        for (Future<T> f : futures) {
            long rem = end - System.nanoTime();
            if (rem <= 0) {
                f.cancel(true);
            } else {
                try {
                    if (!f.isDone()) {
                        f.get(rem, TimeUnit.NANOSECONDS);
                    }
                } catch (TimeoutException e) {
                    f.cancel(true);
                } catch (ExecutionException | CancellationException ignored) {
                }
            }
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try {
            return invokeAny(tasks, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null || tasks.isEmpty()) throw new IllegalArgumentException();
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<>();
        ExecutionException lastEx = null;

        for (Callable<T> t : tasks) {
            futures.add(submit(t));
        }

        try {
            for (Future<T> f : futures) {
                long left = deadline - System.nanoTime();
                if (left <= 0) break;
                try {
                    return f.get(left, TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    lastEx = e;
                } catch (TimeoutException e) {
                    break;
                }
            }
        } finally {
            for (Future<T> f : futures) {
                if (!f.isDone()) f.cancel(true);
            }
        }

        if (lastEx != null) throw lastEx;
        throw new TimeoutException();
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException();
        if (stopped) throw new RejectedExecutionException();
        addToQueue(command);
    }
}