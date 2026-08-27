package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Preparsed caches live inside the Wasm instance, so the pool must reach every engine. */
public class CedarEnginePoolCacheTest {

    private static final String SCHEMA =
            "entity User;\n"
                    + "entity Doc;\n"
                    + "action view appliesTo { principal: [User], resource: [Doc] };\n";

    private static PolicySet permitAll() {
        return PolicySet.of(Policy.of("permit(principal, action, resource);", "p0"));
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .principal("User", "alice")
                .action("Action", "view")
                .resource("Doc", "d1")
                .build();
    }

    @Test
    void cachedPolicySetReachesEveryEngineIncludingLazilyCreatedOnes() throws Exception {
        try (CedarEnginePool pool = CedarEnginePool.create(4)) {
            // Create one engine before caching, so both paths are covered.
            try (CedarEnginePool.Loan warm = pool.borrow()) {
                assertTrue(
                        warm.engine()
                                .isAuthorized(request(), permitAll(), Collections.emptySet())
                                .isAllowed());
            }

            pool.cachePolicySet("ps", permitAll());

            List<CedarEnginePool.Loan> loans = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) {
                    loans.add(pool.borrow());
                }
                for (CedarEnginePool.Loan loan : loans) {
                    AuthorizationResponse response =
                            loan.engine()
                                    .isAuthorizedCached(request(), "ps", Collections.emptySet());
                    assertTrue(
                            response.isSuccess(),
                            "every pooled engine must resolve the id, errors: "
                                    + response.errors());
                    assertEquals(Decision.ALLOW, response.decision());
                }
            } finally {
                for (CedarEnginePool.Loan loan : loans) {
                    loan.close();
                }
            }
        }
    }

    @Test
    void cachedSchemaReachesEveryEngine() throws Exception {
        try (CedarEnginePool pool = CedarEnginePool.create(2)) {
            pool.cacheSchema("s", Schema.fromCedar(SCHEMA));
            pool.cachePolicySet("ps", permitAll());

            List<CedarEnginePool.Loan> loans = new ArrayList<>();
            try {
                for (int i = 0; i < 2; i++) {
                    loans.add(pool.borrow());
                }
                for (CedarEnginePool.Loan loan : loans) {
                    AuthorizationResponse response =
                            loan.engine()
                                    .isAuthorizedCached(
                                            request(), "ps", "s", Collections.emptySet());
                    assertTrue(response.isSuccess(), "errors: " + response.errors());
                }
            } finally {
                for (CedarEnginePool.Loan loan : loans) {
                    loan.close();
                }
            }
        }
    }

    @Test
    void anInvalidPolicySetFailsAtTheCachingCall() throws Exception {
        try (CedarEnginePool pool = CedarEnginePool.create(2)) {
            assertThrows(
                    CedarException.class,
                    () ->
                            pool.cachePolicySet(
                                    "bad", PolicySet.of(Policy.of("this is not cedar", "p0"))));
        }
    }

    @Test
    void cachingIsVisibleToConcurrentBorrowers() throws Exception {
        int threads = 4;
        try (CedarEnginePool pool = CedarEnginePool.create(threads)) {
            pool.cachePolicySet("ps", permitAll());

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger allowed = new AtomicInteger();
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(
                            executor.submit(
                                    () -> {
                                        start.await();
                                        try (CedarEnginePool.Loan loan = pool.borrow()) {
                                            if (loan.engine()
                                                    .isAuthorizedCached(
                                                            request(), "ps", Collections.emptySet())
                                                    .isAllowed()) {
                                                allowed.incrementAndGet();
                                            }
                                        }
                                        return null;
                                    }));
                }
                start.countDown();
                for (java.util.concurrent.Future<?> f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }
                assertEquals(threads, allowed.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void cachingOnAClosedPoolIsRejected() throws Exception {
        CedarEnginePool pool = CedarEnginePool.create(1);
        pool.close();

        assertThrows(IllegalStateException.class, () -> pool.cachePolicySet("ps", permitAll()));
    }

    @Test
    void aBareEngineStillOnlySeesItsOwnCache() {
        // Per-engine scope is intentional; the pool API is what spans engines.
        CedarEngine one = CedarEngine.create();
        CedarEngine two = CedarEngine.create();
        one.cachePolicySet("ps", permitAll());

        AuthorizationResponse response =
                two.isAuthorizedCached(request(), "ps", Collections.emptySet());

        assertFalse(response.isSuccess());
        assertEquals(Decision.DENY, response.decision());
    }
}
