PROJECT := cedar

# Ensure cargo (installed via rustup) is on PATH
export PATH := $(HOME)/.cargo/bin:$(PATH)

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

.PHONY: build
build:
	cargo build --target wasm32-unknown-unknown --release

.PHONY: release
release:
	mkdir -p wasm
	cp target/wasm32-unknown-unknown/release/cedar_wasm.wasm wasm/$(PROJECT).wasm
	@echo "Built wasm/$(PROJECT).wasm ($$(wc -c < wasm/$(PROJECT).wasm) bytes)"

.PHONY: local
local: build release

.PHONY: all
all: get-rust local
