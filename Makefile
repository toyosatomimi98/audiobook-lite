SHELL := /bin/sh
GRADLE := sh ./gradlew

.PHONY: doctor portable test lint build check apk clean devices

doctor:
	bash scripts/ubuntu-doctor.sh

portable:
	bash scripts/check-portability.sh

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

build:
	$(GRADLE) assembleDebug

check: portable test lint

apk: build
	@find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print

devices:
	@command -v adb >/dev/null 2>&1 && adb devices -l || echo "adb not installed or not on PATH"

clean:
	$(GRADLE) clean
