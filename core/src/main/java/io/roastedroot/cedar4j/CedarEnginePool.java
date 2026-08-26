package io.roastedroot.cedar4j;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class CedarEnginePool implements AutoCloseable {
    private final ConcurrentLinkedDeque<PooledEngine> pool;
    private final Semaphore semaphore;
    private final Supplier<CedarEngine> engineFactory;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Caching calls, replayed onto each engine as it is borrowed. */
    private final List<CacheOp> cacheOps = new CopyOnWriteArrayList<>();

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

    /** Preparse a policy set under {@code id} on every engine this pool hands out. */
    public void cachePolicySet(String id, PolicySet policySet) {
        record(new CacheOp(id, policySet, null));
    }

    /** Preparse a schema under {@code id} on every engine this pool hands out. */
    public void cacheSchema(String id, Schema schema) {
        record(new CacheOp(id, null, schema));
    }

    private void record(CacheOp op) {
        checkNotClosed();
        // Apply once up front so a bad payload fails here, not on a later borrow.
        Loan loan;
        try {
            loan = borrow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CedarException("Interrupted while caching " + op.id, e);
        }
        try {
            op.applyTo(loan.engine());
            cacheOps.add(op);
            loan.pooled.syncedOps = cacheOps.size();
        } finally {
            loan.close();
        }
    }

    public Loan borrow() throws InterruptedException {
        checkNotClosed();
        semaphore.acquire();
        try {
            checkNotClosed();
            return new Loan(acquireEngine());
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
            return new Loan(acquireEngine());
        } catch (RuntimeException | Error e) {
            semaphore.release();
            throw e;
        }
    }

    private PooledEngine acquireEngine() {
        PooledEngine pooled = pool.pollFirst();
        if (pooled == null) {
            pooled = new PooledEngine(engineFactory.get());
        }
        syncCache(pooled);
        return pooled;
    }

    /** Replay unseen caching calls. Only ever called by the thread that owns the engine. */
    private void syncCache(PooledEngine pooled) {
        int total = cacheOps.size();
        for (int i = pooled.syncedOps; i < total; i++) {
            cacheOps.get(i).applyTo(pooled.engine);
        }
        pooled.syncedOps = total;
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            drainPool();
        }
    }

    private void drainPool() {
        PooledEngine e;
        while ((e = pool.pollFirst()) != null) {
            e.engine.close();
        }
    }

    private static final class PooledEngine {
        private final CedarEngine engine;
        private int syncedOps;

        private PooledEngine(CedarEngine engine) {
            this.engine = engine;
        }
    }

    private static final class CacheOp {
        private final String id;
        private final PolicySet policySet;
        private final Schema schema;

        private CacheOp(String id, PolicySet policySet, Schema schema) {
            this.id = id;
            this.policySet = policySet;
            this.schema = schema;
        }

        private void applyTo(CedarEngine engine) {
            if (policySet != null) {
                engine.cachePolicySet(id, policySet);
            } else {
                engine.cacheSchema(id, schema);
            }
        }
    }

    public final class Loan implements AutoCloseable {
        private PooledEngine pooled;

        private Loan(PooledEngine pooled) {
            this.pooled = pooled;
        }

        public CedarEngine engine() {
            if (pooled == null) {
                throw new IllegalStateException("Loan already closed or discarded");
            }
            return pooled.engine;
        }

        public void discard() {
            if (pooled != null) {
                pooled.engine.close();
                pooled = null;
                semaphore.release();
            }
        }

        @Override
        public void close() {
            if (pooled != null) {
                pool.offerFirst(pooled);
                if (closed.get()) {
                    drainPool();
                }
                pooled = null;
                semaphore.release();
            }
        }
    }
}
