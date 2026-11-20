import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { createSyncServer } from "../src/server.mjs";

const TOKEN = "test-token";
const BOOK_KEY = "a".repeat(64);
const REPOSITORY_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

test("rejects missing authentication", async (context) => {
  const fixture = await startFixture(context);
  const response = await fetch(`${fixture.baseUrl}/api/v1/progress`);
  assert.equal(response.status, 401);
});

test("stores and returns progress", async (context) => {
  const fixture = await startFixture(context);
  const put = await fetch(`${fixture.baseUrl}/api/v1/progress/${BOOK_KEY}`, {
    method: "PUT",
    headers: authHeaders(),
    body: JSON.stringify(progress(1000)),
  });
  assert.equal(put.status, 200);
  const accepted = await put.json();
  assert.equal(accepted.record.revision, 1);
  assert.equal(accepted.record.percent, 0.42);

  const get = await fetch(`${fixture.baseUrl}/api/v1/progress`, {
    headers: authHeaders(),
  });
  assert.equal(get.status, 200);
  const snapshot = await get.json();
  assert.equal(snapshot.records.length, 1);
  assert.equal(snapshot.records[0].bookKey, BOOK_KEY);
});

test("rejects an older offline update with a conflict", async (context) => {
  const fixture = await startFixture(context);
  await fetch(`${fixture.baseUrl}/api/v1/progress/${BOOK_KEY}`, {
    method: "PUT",
    headers: authHeaders(),
    body: JSON.stringify(progress(2000)),
  });

  const stale = await fetch(`${fixture.baseUrl}/api/v1/progress/${BOOK_KEY}`, {
    method: "PUT",
    headers: authHeaders(),
    body: JSON.stringify(progress(1000)),
  });
  assert.equal(stale.status, 409);
  const conflict = await stale.json();
  assert.equal(conflict.error, "stale_progress");
  assert.equal(conflict.record.clientUpdatedAt, 2000);
});

test("accepts a newer progress event and increments revision", async (context) => {
  const fixture = await startFixture(context);
  for (const updatedAt of [1000, 3000]) {
    const response = await fetch(`${fixture.baseUrl}/api/v1/progress/${BOOK_KEY}`, {
      method: "PUT",
      headers: authHeaders(),
      body: JSON.stringify(progress(updatedAt)),
    });
    assert.equal(response.status, 200);
  }
  const response = await fetch(`${fixture.baseUrl}/api/v1/progress/${BOOK_KEY}`, {
    headers: authHeaders(),
  });
  const snapshot = await response.json();
  assert.equal(snapshot.record.revision, 2);
  assert.equal(snapshot.record.clientUpdatedAt, 3000);
});

async function startFixture(context) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "pagevoice-sync-"));
  const server = createSyncServer({
    webRoot: path.join(REPOSITORY_ROOT, "web"),
    dataDirectory: directory,
    token: TOKEN,
    clock: () => 9999,
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(directory, { recursive: true, force: true });
  });
  const address = server.address();
  return { baseUrl: `http://127.0.0.1:${address.port}` };
}

function authHeaders() {
  return {
    Authorization: `Bearer ${TOKEN}`,
    "Content-Type": "application/json",
  };
}

function progress(clientUpdatedAt) {
  return {
    chapterIndex: 2,
    charOffset: 120,
    pageIndex: 8,
    percent: 0.42,
    clientUpdatedAt,
    deviceId: "test-device",
    baseRevision: 0,
  };
}
