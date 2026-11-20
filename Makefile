SHELL := /bin/sh
GRADLE := sh ./gradlew

.PHONY: doctor portable test lint build check apk clean devices web-test sync-test sync-dev

doctor:
	bash scripts/ubuntu-doctor.sh

portable:
	bash scripts/check-portability.sh

web-test:
	node --test web/test/hash.test.mjs

sync-test:
	node --test sync-server/test/server.test.mjs

sync-dev:
	node sync-server/src/server.mjs

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

build:
	$(GRADLE) assembleDebug

check: portable web-test sync-test test lint

apk: build
	@find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print

devices:
	@command -v adb >/dev/null 2>&1 && adb devices -l || echo "adb not installed or not on PATH"

clean:
	$(GRADLE) clean
