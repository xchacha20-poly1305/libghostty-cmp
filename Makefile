GRADLEW := ./gradlew
GRADLE_FLAGS ?=
TEST ?=
ZIG_TARGET ?=

.DEFAULT_GOAL := help

.PHONY: help assemble build-libs clean \
	test test-android shrink-check check \
	demo-desktop demo-android demo-web test-web test-web-setup npm-lock \
	ghostty-source ghostty-prebuilt gen-kotlin zig-build \
	publish-local publish-snapshot

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make <target> [VAR=value]\n\nTargets:\n"} \
		/^[a-zA-Z0-9_-]+:.*?##/ {printf "  %-18s %s\n", $$1, $$2} \
		/^##@/ {printf "\n%s\n", substr($$0, 5)}' $(MAKEFILE_LIST)

##@ Build
assemble: ## Build all modules
	$(GRADLEW) $(GRADLE_FLAGS) assemble

build-libs: ## Build libraries and desktop/Android demos (skip :demo-web)
	$(GRADLEW) $(GRADLE_FLAGS) assemble -x :demo-web:assemble

clean: ## Clean Gradle build outputs
	$(GRADLEW) $(GRADLE_FLAGS) clean

##@ Test & Verify
test: ## Run desktop JVM tests (optional: TEST='*GhosttyVtBridgeTest')
	$(GRADLEW) $(GRADLE_FLAGS) :library:desktopTest :library-extras:desktopTest $(if $(TEST),--tests '$(TEST)',)

test-android: ## Run Android instrumented tests on a connected device/emulator
	$(GRADLEW) $(GRADLE_FLAGS) :library:connectedAndroidDeviceTest :library-extras:connectedAndroidDeviceTest $(if $(TEST),--tests '$(TEST)',)

shrink-check: ## Verify R8 keep rules and JNI loading
	$(GRADLEW) $(GRADLE_FLAGS) :shrink-check:shrinkCheck

check: test shrink-check ## Run desktop tests and R8 shrink check

##@ Demos & Web
demo-desktop: ## Run the desktop terminal demo
	$(GRADLEW) $(GRADLE_FLAGS) :demo-desktop:run

demo-android: ## Install the Android debug demo on a connected device/emulator
	$(GRADLEW) $(GRADLE_FLAGS) :demo-android:installDebug

demo-web: ## Build standalone web offline ZIPs and Pages output
	$(GRADLEW) $(GRADLE_FLAGS) :demo-web:offlineZip :demo-web:preparePages

test-web-setup: ## Install Playwright and browser dependencies for web tests
	cd demo-web/browser-tests && npm ci && npx playwright install

test-web: demo-web ## Build web demo and run Playwright browser tests
	cd demo-web/browser-tests && npm test

npm-lock: ## Update Kotlin JS/Wasm npm package-lock.json files
	$(GRADLEW) $(GRADLE_FLAGS) kotlinUpgradePackageLock kotlinWasmUpgradePackageLock

##@ Native & Ghostty
ghostty-source: ## Fetch and patch the pinned Ghostty source
	$(GRADLEW) $(GRADLE_FLAGS) ghosttySource

ghostty-prebuilt: ## Build and export prebuilt native outputs to build/ghostty/prebuilt
	$(GRADLEW) $(GRADLE_FLAGS) ghosttyPrebuilt

gen-kotlin: ghostty-source ## Regenerate Kotlin enums (GhosttyKey, GhosttyMods) via Zig
	cd native && zig build gen-kotlin

zig-build: ghostty-source ## Build native Zig bridge (optional: ZIG_TARGET=x86_64-linux-gnu)
	cd native && zig build $(if $(ZIG_TARGET),-Dtarget=$(ZIG_TARGET),)

##@ Publish
publish-local: ## Publish artifacts to local Maven repository (~/.m2)
	$(GRADLEW) $(GRADLE_FLAGS) publishToMavenLocal

publish-snapshot: ## Publish snapshot artifacts to Maven Central
	$(GRADLEW) $(GRADLE_FLAGS) publishToMavenCentral
