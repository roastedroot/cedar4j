package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class CedarExtensionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extReturnsLong() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "text_length",
                                args -> IntNode.valueOf(args.get(0).asText().length()))
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "hello world");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"text_length\", context.text) > 5 };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertTrue(response.isAllowed());
    }

    @Test
    void extReturnsLongDeny() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "text_length",
                                args -> IntNode.valueOf(args.get(0).asText().length()))
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "hi");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"text_length\", context.text) > 5 };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertFalse(response.isAllowed());
    }

    @Test
    void extReturnsBool() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "is_safe",
                                args ->
                                        BooleanNode.valueOf(
                                                !args.get(0).asText().contains("unsafe")))
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("message", "safe content");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "post")
                        .resource("Resource", "channel1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"is_safe\", context.message) };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertTrue(response.isAllowed());
    }

    @Test
    void extReturnsString() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction("classify", args -> TextNode.valueOf("safe"))
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "hello");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"classify\", context.text)"
                                        + " == \"safe\" };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertTrue(response.isAllowed());
    }

    @Test
    void extReturnsRecord() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "analyze",
                                args -> {
                                    var node = MAPPER.createObjectNode();
                                    node.put("score", 42);
                                    node.put("label", "safe");
                                    return node;
                                })
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "hello world");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policiesScore =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"analyze\", context.text).score"
                                        + " < 50 };",
                                "p0"));

        assertTrue(engine.isAuthorized(request, policiesScore, Collections.emptySet()).isAllowed());

        PolicySet policiesLabel =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"analyze\", context.text).label"
                                        + " == \"safe\" };",
                                "p0"));

        assertTrue(engine.isAuthorized(request, policiesLabel, Collections.emptySet()).isAllowed());
    }

    @Test
    void extMultipleCallbacks() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "text_length",
                                args -> IntNode.valueOf(args.get(0).asText().length()))
                        .extensionFunction(
                                "is_safe",
                                args ->
                                        BooleanNode.valueOf(
                                                !args.get(0).asText().contains("unsafe")))
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "hello world");
        context.put("message", "safe content");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "post")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"text_length\", context.text) > 5"
                                        + " && ext(\"is_safe\", context.message) };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertTrue(response.isAllowed());
    }

    @Test
    void extCallbackThrowsSkipsPolicy() {
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "always_fails",
                                args -> {
                                    throw new RuntimeException("intentional failure");
                                })
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "anything");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { ext(\"always_fails\", context.text)"
                                        + " > 0 };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertFalse(response.isAllowed());
        assertFalse(response.errors().isEmpty());
    }

    @Test
    void backwardCompatibility() {
        CedarEngine engine = CedarEngine.create();

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies = PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertTrue(response.isAllowed());
    }

    @Test
    void extLazyEvaluation() {
        AtomicInteger callCount = new AtomicInteger(0);
        CedarEngine engine =
                CedarEngine.builder()
                        .extensionFunction(
                                "expensive",
                                args -> {
                                    callCount.incrementAndGet();
                                    return IntNode.valueOf(42);
                                })
                        .build();

        Map<String, Object> context = new HashMap<>();
        context.put("text", "anything");

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { false && ext(\"expensive\", context.text)"
                                        + " > 0 };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());
        assertFalse(response.isAllowed());
        assertTrue(
                callCount.get() == 0,
                "expensive callback should not be called due to && short-circuit");
    }

    @Test
    void poolWithExtensions() throws Exception {
        CedarEnginePool pool =
                CedarEnginePool.create(
                        2,
                        () ->
                                CedarEngine.builder()
                                        .extensionFunction(
                                                "text_length",
                                                args ->
                                                        IntNode.valueOf(
                                                                args.get(0).asText().length()))
                                        .build());

        AtomicBoolean allPassed = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            new Thread(
                            () -> {
                                try (CedarEnginePool.Loan loan = pool.borrow()) {
                                    Map<String, Object> context = new HashMap<>();
                                    context.put("text", "hello world");

                                    AuthorizationRequest request =
                                            AuthorizationRequest.builder()
                                                    .principal("User", "alice")
                                                    .action("Action", "view")
                                                    .resource("Resource", "doc1")
                                                    .context(context)
                                                    .build();

                                    PolicySet policies =
                                            PolicySet.of(
                                                    Policy.of(
                                                            "permit(principal,action,resource)"
                                                                    + " when {"
                                                                    + " ext(\"text_length\","
                                                                    + " context.text) > 5 };",
                                                            "p0"));

                                    AuthorizationResponse response =
                                            loan.engine()
                                                    .isAuthorized(
                                                            request,
                                                            policies,
                                                            Collections.emptySet());
                                    if (!response.isAllowed()) {
                                        allPassed.set(false);
                                    }
                                } catch (Exception e) {
                                    allPassed.set(false);
                                } finally {
                                    latch.countDown();
                                }
                            })
                    .start();
        }

        latch.await();
        assertTrue(allPassed.get());
    }
}
