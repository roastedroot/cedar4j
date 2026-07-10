# cedar4j

Cedar policy engine for Java via WebAssembly.

Evaluates [Cedar](https://www.cedarpolicy.com/) authorization policies in pure Java
by compiling the Cedar Rust crate to Wasm and running it through
[Endive](https://endive.run/). No JNI, no native binaries — runs everywhere the JVM runs.

## Usage

```xml
<dependency>
  <groupId>io.roastedroot</groupId>
  <artifactId>cedar4j</artifactId>
  <version>${cedar4j.version}</version>
</dependency>
```

### Authorize

```java
CedarEngine engine = CedarEngine.create();

AuthorizationRequest request = AuthorizationRequest.builder()
    .principal("User", "alice")
    .action("Action", "view")
    .resource("Document", "doc1")
    .build();

PolicySet policies = PolicySet.of(
    Policy.of("permit(principal, action, resource);", "allow-all"));

AuthorizationResponse response = engine.isAuthorized(
    request, policies, Collections.emptySet());

response.isAllowed();  // true
response.decision();   // Decision.ALLOW
response.reasons();    // ["allow-all"]
```

### Entities and context

```java
EntityUID alice = EntityUID.of("User", "alice");
EntityUID admins = EntityUID.of("Group", "admins");

Set<Entity> entities = Set.of(
    Entity.of(alice, Set.of(admins)),
    Entity.of(admins));

Map<String, Object> context = Map.of("authenticated", true);

AuthorizationRequest request = AuthorizationRequest.builder()
    .principal(alice)
    .action("Action", "delete")
    .resource("Document", "doc1")
    .context(context)
    .build();

PolicySet policies = PolicySet.of(Policy.of(
    "permit(principal, action, resource) when { principal in Group::\"admins\" };",
    "admin-access"));

AuthorizationResponse response = engine.isAuthorized(request, policies, entities);
```

### Validation

```java
Schema schema = Schema.fromCedar(
    "entity User;\n"
    + "entity Photo;\n"
    + "action viewPhoto appliesTo { principal: [User], resource: [Photo] };");

ValidationRequest request = ValidationRequest.builder()
    .schema(schema)
    .policies(policies)
    .build();

ValidationResponse response = engine.validate(request);
response.isValid();              // true if no validation errors
response.validationErrors();     // list of ValidationError
```

### Caching

Pre-parse policies for repeated evaluations:

```java
engine.cachePolicySet("my-policies", policies);

AuthorizationResponse response = engine.isAuthorizedCached(
    request, "my-policies", entities);
```

### Partial authorization

Evaluate with unknown principal, action, or resource:

```java
PartialAuthorizationRequest partial = PartialAuthorizationRequest.builder()
    .action("Action", "view")
    .resource("Document", "doc1")
    .build();

PartialAuthorizationResponse response = engine.isAuthorizedPartial(
    partial, policies, entities);

response.decision();            // ALLOW or DENY
response.nontrivialResiduals(); // policies that couldn't be fully evaluated
```

### Concurrent use

Wasm instances are not thread-safe. Use `CedarEnginePool` for concurrent access:

```java
CedarEnginePool pool = CedarEnginePool.create(4);

try (CedarEnginePool.Loan loan = pool.borrow()) {
    AuthorizationResponse response = loan.engine().isAuthorized(
        request, policies, entities);
}
```

### Custom ObjectMapper

```java
CedarEngine engine = CedarEngine.builder()
    .withObjectMapper(myCustomMapper)
    .build();
```

### Raw JSON API

A low-level string-based API is available for advanced use cases via `engine.raw()`:

```java
String result = engine.raw().authorize(requestJson);
String parsed = engine.raw().parsePolicy(policyText);
String formatted = engine.raw().formatPolicies(policiesText);
```

See `CedarRawEngine` for the full list of raw methods.

## Building

```bash
mvn clean verify
```

To rebuild the Wasm binary (requires Rust):

```bash
cd wasm-build
make all
```

## License

Apache-2.0
