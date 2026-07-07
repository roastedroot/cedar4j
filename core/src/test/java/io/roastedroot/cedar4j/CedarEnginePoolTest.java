package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public class CedarEnginePoolTest {

    private static final AuthorizationRequest REQUEST =
            AuthorizationRequest.builder()
                    .principal("User", "alice")
                    .action("Action", "view")
                    .resource("Resource", "doc1")
                    .build();

    private static final PolicySet POLICIES =
            PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));

    @Test
    void borrowAndReturn() throws Exception {
        CedarEnginePool pool = CedarEnginePool.create(2);
        try (CedarEnginePool.Loan loan = pool.borrow()) {
            AuthorizationResponse response =
                    loan.engine()
                            .isAuthorized(REQUEST, POLICIES, Collections.emptySet());
            assertTrue(response.isAllowed());
        }
    }

    @Test
    void borrowAndDiscard() throws Exception {
        CedarEnginePool pool = CedarEnginePool.create(2);
        CedarEnginePool.Loan loan = pool.borrow();
        assertNotNull(loan.engine());
        loan.discard();
    }

    @Test
    void concurrentAccess() throws Exception {
        int poolSize = 4;
        int taskCount = 20;
        CedarEnginePool pool = CedarEnginePool.create(poolSize);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<AuthorizationResponse>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    latch.await();
                                    try (CedarEnginePool.Loan loan = pool.borrow()) {
                                        return loan.engine()
                                                .isAuthorized(
                                                        REQUEST,
                                                        POLICIES,
                                                        Collections.emptySet());
                                    }
                                }));
            }

            latch.countDown();

            for (Future<AuthorizationResponse> future : futures) {
                AuthorizationResponse response = future.get();
                assertTrue(response.isAllowed(), "Expected allow, got: " + response);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
