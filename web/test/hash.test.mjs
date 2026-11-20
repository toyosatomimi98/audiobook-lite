import assert from "node:assert/strict";
import test from "node:test";
import { sha256Fallback, toHex } from "../lib/hash.js";

test("fallback SHA-256 matches the standard vector", () => {
  const hash = sha256Fallback(new TextEncoder().encode("abc"));
  assert.equal(
    toHex(hash),
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
  );
});

test("fallback SHA-256 handles UTF-8 book titles", () => {
  const hash = sha256Fallback(new TextEncoder().encode("页声"));
  assert.equal(hash.length, 32);
});
