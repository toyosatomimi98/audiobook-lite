import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const SCHEMA_VERSION = 1;

export class ProgressStore {
  constructor(dataDirectory, clock = () => Date.now()) {
    this.dataDirectory = dataDirectory;
    this.clock = clock;
    this.filePath = path.join(dataDirectory, "progress.json");
    fs.mkdirSync(dataDirectory, { recursive: true });
    this.state = this.#load();
  }

  all() {
    return Object.values(this.state.records).sort((left, right) =>
      right.clientUpdatedAt - left.clientUpdatedAt
    );
  }

  get(bookKey) {
    return this.state.records[bookKey] ?? null;
  }

  upsert(bookKey, event) {
    const current = this.get(bookKey);
    if (current && !isNewer(event, current)) {
      return { accepted: false, record: current };
    }

    const accepted = {
      bookKey,
      chapterIndex: event.chapterIndex,
      charOffset: event.charOffset,
      pageIndex: event.pageIndex,
      percent: event.percent,
      clientUpdatedAt: event.clientUpdatedAt,
      serverUpdatedAt: this.clock(),
      revision: (current?.revision ?? 0) + 1,
      deviceId: event.deviceId,
    };
    this.state.records[bookKey] = accepted;
    this.#persist();
    return { accepted: true, record: accepted };
  }

  #load() {
    if (!fs.existsSync(this.filePath)) {
      return { schemaVersion: SCHEMA_VERSION, records: {} };
    }
    const parsed = JSON.parse(fs.readFileSync(this.filePath, "utf8"));
    if (parsed.schemaVersion !== SCHEMA_VERSION || typeof parsed.records !== "object") {
      throw new Error("Unsupported sync data file.");
    }
    return parsed;
  }

  #persist() {
    const temporary = `${this.filePath}.${process.pid}.tmp`;
    fs.writeFileSync(temporary, `${JSON.stringify(this.state, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600,
    });
    fs.renameSync(temporary, this.filePath);
  }
}

function isNewer(incoming, current) {
  if (incoming.clientUpdatedAt !== current.clientUpdatedAt) {
    return incoming.clientUpdatedAt > current.clientUpdatedAt;
  }
  return incoming.deviceId.localeCompare(current.deviceId) > 0;
}

export function loadOrCreateToken(dataDirectory) {
  const configured = process.env.PAGEVOICE_SYNC_TOKEN?.trim();
  if (configured) return configured;

  fs.mkdirSync(dataDirectory, { recursive: true });
  const tokenPath = path.join(dataDirectory, "token");
  if (fs.existsSync(tokenPath)) {
    return fs.readFileSync(tokenPath, "utf8").trim();
  }

  const token = crypto.randomBytes(24).toString("hex");
  fs.writeFileSync(tokenPath, `${token}\n`, { encoding: "utf8", mode: 0o600 });
  return token;
}
