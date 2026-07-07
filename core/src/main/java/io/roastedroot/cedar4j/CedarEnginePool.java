package io.roastedroot.cedar4j;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class CedarEnginePool implements AutoCloseable {
    private final ConcurrentLinkedDeque<CedarEngine> pool;
    private final Semaphore semaphore;
    private final Supplier<CedarEngine> engineFactory;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private CedarEnginePool(int maxSize, Supplier<CedarEngine> engineFactory) {
        this.pool = new ConcurrentLinkedDeque<>();
        this.semaphore = new Semaphore(maxSize);
        this.engineFactory = engineFactory;
    }

    public static CedarEnginePool create(int maxSize) {
        return create(maxSize, CedarEngine::create);
    }

    public static CedarEnginePool create(int maxSize, Supplier<CedarEngine> engineFactory) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        return new CedarEnginePool(maxSize, engineFactory);
    }

    public Loan borrow() throws InterruptedException {
        checkNotClosed();
        semaphore.acquire();
        try {
            checkNotClosed();
            CedarEngine engine = pool.pollFirst();
            if (engine == null) {
                engine = engineFactory.get();
            }
            return new Loan(engine);
        } catch (RuntimeException | Error e) {
            semaphore.release();
            throw e;
        }
    }

    public Loan tryBorrow(long timeout, TimeUnit unit) throws InterruptedException {
        checkNotClosed();
        if (!semaphore.tryAcquire(timeout, unit)) {
            return null;
        }
        try {
            checkNotClosed();
            CedarEngine engine = pool.pollFirst();
            if (engine == null) {
                engine = engineFactory.get();
            }
            return new Loan(engine);
        } catch (RuntimeException | Error e) {
            semaphore.release();
            throw e;
        }
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            CedarEngine engine;
            while ((engine = pool.pollFirst()) != null) {
                engine.close();
            }
        }
    }

    public final class Loan implements AutoCloseable {
        private CedarEngine engine;

        private Loan(CedarEngine engine) {
            this.engine = engine;
        }

        public CedarEngine engine() {
            if (engine == null) {
                throw new IllegalStateException("Loan already closed or discarded");
            }
            return engine;
        }

        public void discard() {
            if (engine != null) {
                engine.close();
                engine = null;
                semaphore.release();
            }
        }

        @Override
        public void close() {
            if (engine != null) {
                if (closed.get()) {
                    engine.close();
                } else {
                    pool.offerFirst(engine);
                }
                engine = null;
                semaphore.release();
            }
        }
    }
}
