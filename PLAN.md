# cedar4j — Follow-up Plan

## Current State

- 22 production Java files, 3 test files
- 59,840 tests green (26 unit + 59,814 Cedar conformance suite)
- Typed APIs: isAuthorized, isAuthorizedPartial, isAuthorizedCached, validate, validateEntities
- Caching: cachePolicySet, cacheSchema, isAuthorizedCached (with schema support)
- Thread-safe pooling via CedarEnginePool
- All accessors use `x()` style, private constructors + factory/builder pattern

## Follow-ups

### 1. Git init + first release
- [ ] `git init`, first commit, push to GitHub
- [ ] Tag `0.1.0` release
- [ ] Publish to Maven Central

### 2. Pin Cedar version
- [ ] Replace `branch = "main"` in Cargo.toml with a specific release tag
- [ ] Rebuild wasm binary against the pinned version
- [ ] Document the Cedar version in Readme

### 3. PLAN.md → GitHub Issues
- [ ] Remove PLAN.md from the repo
- [ ] Create GitHub issues for the remaining gaps below

### 4. Typed `validateWithLevel` API
- [ ] `LevelValidationRequest` with builder (schema, policies, maxDerefLevel)
- [ ] Maps to `wasm.call("ValidateWithLevelOperation", json)`
- [ ] Reuses `ValidationResponse`

### 5. Typed `PolicySet.parsePolicies()`
- [ ] Static factory: `PolicySet.parse(String cedarText, CedarEngine engine)`
- [ ] Parses Cedar text into individual policies with proper IDs
- [ ] Currently done manually in the integration test harness

### 6. CI improvements
- [ ] Fix CI matrix: JDK 11/25 — verify JDK 25 is available in GitHub Actions
- [ ] Add build-wasm.yml workflow for automated Wasm rebuilds
- [ ] Add code coverage reporting

### 7. Documentation
- [ ] Javadoc on all public classes
- [ ] Update Readme with validation, caching, partial auth examples
- [ ] Add CONTRIBUTING.md

### 8. Performance
- [ ] JMH benchmark module (compare against cedar-java JNI)
- [ ] Profile Endive compilation time — consider pre-declaring interpreted functions

### 9. Custom Extension Functions

Enable users to register Java callbacks as Cedar extension functions, callable from policies.
Cedar's evaluator has a plugin mechanism for extension types (see the Cedar paper, §Full Cedar:
"The Cedar evaluator implementation provides a plugin mechanism for new extension types").
cedar4j should expose this through the Wasm host import boundary.

#### 9.1 Java API

```java
CedarEngine engine = CedarEngine.builder()
    // unary: one Cedar value in, one Cedar value out
    .extensionFunction("llm_score", CedarType.STRING, CedarType.LONG,
        arg -> CedarValue.ofLong(myScorer.score(arg.asString())))

    // unary returning boolean
    .extensionFunction("is_safe", CedarType.STRING, CedarType.BOOLEAN,
        arg -> CedarValue.ofBoolean(myFilter.check(arg.asString())))

    // binary: two args
    .extensionFunction("similarity",
        List.of(CedarType.STRING, CedarType.STRING), CedarType.LONG,
        args -> CedarValue.ofLong(myModel.compare(
            args.get(0).asString(), args.get(1).asString())))
    .build();
```

Cedar policies then use them as normal extension functions:

```cedar
permit(principal, action == Action::"chat", resource)
when { llm_score(context.user_input) < 50 };

forbid(principal, action, resource)
when { !is_safe(context.message) };

permit(principal, action == Action::"search", resource)
when { similarity(context.query, resource.description) > 70 };
```

cedar4j provides the registration mechanism only — the callback implementation
(LLM, regex, database lookup, remote service) is entirely the user's concern.

#### 9.2 Wasm Boundary Plumbing

The call chain for a custom extension function:

```
Cedar policy: llm_score(context.input)
  → Cedar evaluator (Rust): calls registered ExtensionFunction
    → Rust fn body: calls Wasm import `host_extension_call`
      → Endive/Chicory host function (Java): dispatches by name to callback
        → User callback: returns CedarValue
      ← result flows back through each layer
```

**Rust side (lib.rs changes):**

1. Declare a Wasm host import:

   ```rust
   extern "C" {
       fn host_extension_call(
           name_ptr: i32, name_len: i32,
           args_ptr: i32, args_len: i32,
       ) -> i64; // wide pointer to JSON result
   }
   ```

2. New export to register extension definitions before authorization:

   ```rust
   #[no_mangle]
   pub extern "C" fn cedar_register_extensions(ptr: i32, len: i32) -> i64
   ```

   Accepts JSON array of function definitions:
   ```json
   [
     {"name": "llm_score", "arg_types": ["String"], "return_type": "Long"},
     {"name": "is_safe", "arg_types": ["String"], "return_type": "Boolean"},
     {"name": "similarity", "arg_types": ["String", "String"], "return_type": "Long"}
   ]
   ```

   Each registered function creates an `ExtensionFunction` whose body serializes
   args to JSON, calls `host_extension_call`, deserializes the result.

3. The registered extensions are added to the `Extensions` set passed to the
   Cedar `Authorizer`, alongside the built-in extensions (ip, decimal, datetime).

**Java side (CedarWasm / CedarEngine changes):**

1. `CedarEngine.Builder` collects extension function registrations (name, arg types,
   return type, callback).

2. When building the Endive `Instance`, register a host function for the
   `host_extension_call` Wasm import. The host function:
   - Reads the function name from Wasm memory
   - Reads the args JSON from Wasm memory
   - Looks up the Java callback by name
   - Deserializes args into `CedarValue`s, calls the callback
   - Serializes the result to JSON, writes it to Wasm memory
   - Returns a wide pointer to the result

3. After instance creation, call `cedar_register_extensions` with the JSON
   definitions so the Rust side knows the function signatures.

#### 9.3 Type Mapping

| Cedar type | Java representation | Policy usage example |
|------------|--------------------|-----------------------|
| Long       | `CedarValue.ofLong(n)` | `my_fn(x) > 50` |
| String     | `CedarValue.ofString(s)` | `my_fn(x) == "category_a"` |
| Boolean    | `CedarValue.ofBoolean(b)` | `my_fn(x)` / `!my_fn(x)` |
| Decimal    | `CedarValue.ofDecimal(s)` | `my_fn(x).lessThan(decimal("0.7"))` |

Extension functions can accept and return any Cedar value type. Sets and Records
are possible but uncommon for typical callback use cases.

#### 9.4 Error Handling

If a callback throws an exception, cedar4j converts it to a Cedar evaluation error.
Cedar's semantics: a policy that errors during evaluation is skipped (neither permits
nor forbids). This is consistent with how built-in extension functions behave on
invalid input (e.g., `ip("not-an-ip")`).

#### 9.5 Open Questions

1. **Schema integration:** Should custom extension functions be declarable in the
   Cedar schema so `validate()` accepts policies that use them? Without this,
   policies using custom functions will evaluate correctly but fail validation.

2. **Arity limits:** Cedar's built-in extensions go up to variadic. Do we need
   variadic support for custom functions, or is a fixed max arity (e.g., 3) enough?

3. **Caching:** Should cedar4j offer optional memoization of callback results
   within a single `authorize()` call? A policy that calls `llm_score(context.input)`
   in multiple conditions would otherwise invoke the callback multiple times with
   the same argument.

4. **Wasm target:** The current build uses `wasm32-unknown-unknown`. Host imports
   work with this target, but need verification that Endive's host function
   registration is compatible with the generated module interface.
