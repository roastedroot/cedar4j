PROJECT := cedar

# Ensure cargo (installed via rustup) is on PATH
export PATH := $(HOME)/.cargo/bin:$(PATH)

# Cedar checkout path (resolved from Cargo.lock after cargo fetch)
CEDAR_CORE_EXTENSIONS = $(shell cargo metadata --format-version 1 2>/dev/null | \
	python3 -c "import sys,json; pkgs=json.loads(sys.stdin.read())['packages']; \
	print([p['manifest_path'] for p in pkgs if p['name']=='cedar-policy-core'][0].replace('/Cargo.toml','/src/extensions'))" 2>/dev/null)

# =========================================================================
# RECIPES / TARGETS
# =========================================================================
.PHONY: clean
clean:
	cargo clean
	rm -rf wasm

.PHONY: fmt
fmt:
	cargo fmt

.PHONY: get-rust
get-rust:
	@if ! command -v cargo >/dev/null 2>&1; then \
	   echo "Rust/Cargo not found. Installing via rustup..."; \
	   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y && \
	   . "$(HOME)/.cargo/env" && \
	   echo "Rust successfully installed!"; \
	else \
	   echo "Rust/Cargo already installed: $$(cargo --version)"; \
	fi
	@if ! rustup target list --installed | grep -q wasm32-unknown-unknown; then \
	   echo "Adding wasm32-unknown-unknown target..."; \
	   rustup target add wasm32-unknown-unknown && \
	   echo "wasm32-unknown-unknown target added!"; \
	else \
	   echo "wasm32-unknown-unknown target already installed"; \
	fi

# Apply the host extension patch to the cedar-policy-core checkout.
# This copies host.rs and registers it in extensions.rs.
# Needed because Cedar does not yet support external extension registration.
.PHONY: patch
patch:
	@cargo fetch 2>/dev/null
	@if [ -z "$(CEDAR_CORE_EXTENSIONS)" ]; then \
	   echo "ERROR: Could not resolve cedar-policy-core path. Run 'cargo fetch' first."; \
	   exit 1; \
	fi
	@echo "Patching cedar-policy-core at $(CEDAR_CORE_EXTENSIONS)"
	cp cedar-patch/host.rs "$(CEDAR_CORE_EXTENSIONS)/host.rs"
	@if ! grep -q 'pub mod host;' "$(CEDAR_CORE_EXTENSIONS).rs"; then \
	   sed -i 's/pub mod partial_evaluation;/pub mod host;\npub mod partial_evaluation;/' \
	       "$(CEDAR_CORE_EXTENSIONS).rs"; \
	   echo "Added 'pub mod host;' to extensions.rs"; \
	fi
	@if ! grep -q 'host::extension()' "$(CEDAR_CORE_EXTENSIONS).rs"; then \
	   sed -i 's/partial_evaluation::extension(),/partial_evaluation::extension(),\n        host::extension(),/' \
	       "$(CEDAR_CORE_EXTENSIONS).rs"; \
	   echo "Added 'host::extension()' to ALL_AVAILABLE_EXTENSION_OBJECTS"; \
	fi
	@echo "Patch applied successfully"

.PHONY: build
build:
	cargo build --target wasm32-unknown-unknown --release

.PHONY: release
release:
	mkdir -p wasm
	cp target/wasm32-unknown-unknown/release/cedar_wasm.wasm wasm/$(PROJECT).wasm
	@echo "Built wasm/$(PROJECT).wasm ($$(wc -c < wasm/$(PROJECT).wasm) bytes)"

.PHONY: local
local: patch build release

.PHONY: all
all: get-rust local
