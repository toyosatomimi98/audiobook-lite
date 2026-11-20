import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { loadOrCreateToken, ProgressStore } from "./progress-store.mjs";

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(moduleDirectory, "../..");
const defaultWebRoot = path.join(repositoryRoot, "web");
const defaultDataDirectory = path.join(repositoryRoot, "sync-server", "data");
const MAX_BODY_BYTES = 64 * 1024;

export function createSyncServer({
  webRoot = defaultWebRoot,
  dataDirectory = defaultDataDirectory,
  token,
  clock = () => Date.now(),
} = {}) {
  const syncToken = token ?? loadOrCreateToken(dataDirectory);
  const store = new ProgressStore(dataDirectory, clock);

  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (url.pathname === "/api/v1/health" && request.method === "GET") {
        return sendJson(response, 200, {
          ok: true,
          service: "pagevoice-sync",
          serverTime: clock(),
        });
      }

      if (url.pathname.startsWith("/api/")) {
        if (!isAuthorized(request.headers.authorization, syncToken)) {
          return sendJson(response, 401, { error: "unauthorized" });
        }
        return await handleApi(request, response, url, store);
      }

      return serveStatic(request, response, url, webRoot);
    } catch (error) {
      console.error(error);
      if (!response.headersSent) {
        const status = Number.isInteger(error?.statusCode) ? error.statusCode : 500;
        sendJson(response, status, {
          error: status === 500 ? "internal_error" : error.message,
        });
      } else {
        response.end();
      }
    }
  });
}

async function handleApi(request, response, url, store) {
  if (url.pathname === "/api/v1/progress" && request.method === "GET") {
    return sendJson(response, 200, {
      schemaVersion: 1,
      serverTime: Date.now(),
      records: store.all(),
    });
  }

  const match = url.pathname.match(/^\/api\/v1\/progress\/([a-f0-9]{64})$/);
  if (!match) return sendJson(response, 404, { error: "not_found" });
  const bookKey = match[1];

  if (request.method === "GET") {
    const record = store.get(bookKey);
    return record
      ? sendJson(response, 200, { record })
      : sendJson(response, 404, { error: "progress_not_found" });
  }

  if (request.method === "PUT") {
    const body = await readJsonBody(request);
    const validationError = validateProgress(body);
    if (validationError) {
      return sendJson(response, 400, { error: validationError });
    }

    const result = store.upsert(bookKey, body);
    return result.accepted
      ? sendJson(response, 200, { record: result.record })
      : sendJson(response, 409, {
          error: "stale_progress",
          record: result.record,
        });
  }

  return sendJson(response, 405, { error: "method_not_allowed" });
}

function validateProgress(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return "invalid_body";
  const nonNegativeIntegers = ["chapterIndex", "charOffset", "pageIndex"];
  for (const field of nonNegativeIntegers) {
    if (!Number.isSafeInteger(value[field]) || value[field] < 0) {
      return `invalid_${field}`;
    }
  }
  if (typeof value.percent !== "number" ||
    !Number.isFinite(value.percent) ||
    value.percent < 0 ||
    value.percent > 1
  ) {
    return "invalid_percent";
  }
  if (!Number.isSafeInteger(value.clientUpdatedAt) || value.clientUpdatedAt < 1) {
    return "invalid_clientUpdatedAt";
  }
  if (typeof value.deviceId !== "string" ||
    value.deviceId.trim().length === 0 ||
    value.deviceId.length > 128
  ) {
    return "invalid_deviceId";
  }
  if (value.baseRevision !== undefined &&
    (!Number.isSafeInteger(value.baseRevision) || value.baseRevision < 0)
  ) {
    return "invalid_baseRevision";
  }
  return null;
}

async function readJsonBody(request) {
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > MAX_BODY_BYTES) {
      const error = new Error("request_too_large");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    const error = new Error("invalid_json");
    error.statusCode = 400;
    throw error;
  }
}

function isAuthorized(header, token) {
  if (!header?.startsWith("Bearer ")) return false;
  const supplied = Buffer.from(header.slice("Bearer ".length));
  const expected = Buffer.from(token);
  return supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);
}

function serveStatic(request, response, url, webRoot) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return sendJson(response, 405, { error: "method_not_allowed" });
  }

  let requestedPath;
  try {
    requestedPath = decodeURIComponent(url.pathname);
  } catch {
    return sendJson(response, 400, { error: "invalid_path" });
  }
  const relative = requestedPath === "/" ? "index.html" : requestedPath.replace(/^\/+/, "");
  const candidate = path.resolve(webRoot, relative);
  const webRootPrefix = `${path.resolve(webRoot)}${path.sep}`;
  if (candidate !== path.resolve(webRoot) && !candidate.startsWith(webRootPrefix)) {
    return sendJson(response, 403, { error: "forbidden" });
  }

  const filePath = fs.existsSync(candidate) && fs.statSync(candidate).isFile()
    ? candidate
    : path.join(webRoot, "index.html");
  if (!fs.existsSync(filePath)) {
    return sendJson(response, 404, { error: "web_client_not_found" });
  }

  response.writeHead(200, {
    "Content-Type": contentType(filePath),
    "Cache-Control": filePath.endsWith("index.html") || filePath.endsWith("sw.js")
      ? "no-cache"
      : "public, max-age=300",
  });
  if (request.method === "HEAD") return response.end();
  fs.createReadStream(filePath).pipe(response);
}

function contentType(filePath) {
  const extension = path.extname(filePath).toLowerCase();
  return {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".webmanifest": "application/manifest+json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
  }[extension] ?? "application/octet-stream";
}

function sendJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store",
  });
  response.end(body);
}

function startFromEnvironment() {
  const port = Number.parseInt(process.env.PAGEVOICE_SYNC_PORT ?? "8787", 10);
  const host = process.env.PAGEVOICE_SYNC_HOST ?? "127.0.0.1";
  const dataDirectory = process.env.PAGEVOICE_DATA_DIR
    ? path.resolve(process.env.PAGEVOICE_DATA_DIR)
    : defaultDataDirectory;
  const token = loadOrCreateToken(dataDirectory);
  const server = createSyncServer({ dataDirectory, token });
  server.listen(port, host, () => {
    console.log(`PageVoice sync: http://${host}:${port}`);
    console.log(`Sync token: ${token}`);
    console.log("Use PAGEVOICE_SYNC_TOKEN to pin a token in production.");
  });
}

if (process.argv[1] &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
) {
  startFromEnvironment();
}
