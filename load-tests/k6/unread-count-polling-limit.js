import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { SharedArray } from "k6/data";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TARGET_VUS = numberFromEnvironment("TARGET_VUS", 2000);
const POLL_INTERVAL_SECONDS = numberFromEnvironment(
  "POLL_INTERVAL_SECONDS",
  5,
);
const RAMP_UP_SECONDS = numberFromEnvironment("RAMP_UP_SECONDS", 60);
const STEADY_SECONDS = numberFromEnvironment("STEADY_SECONDS", 120);
const RAMP_DOWN_SECONDS = numberFromEnvironment("RAMP_DOWN_SECONDS", 30);
const P95_LIMIT_MS = numberFromEnvironment("P95_LIMIT_MS", 500);
const P99_LIMIT_MS = numberFromEnvironment("P99_LIMIT_MS", 1000);
const TOKEN_FILE =
  __ENV.TOKEN_FILE || "./.tokens/polling-users.json";
const AUTH_MODE = "preissued-unique";

const testUsers = new SharedArray("preissued polling users", () => {
  const parsedUsers = JSON.parse(open(TOKEN_FILE));

  if (!Array.isArray(parsedUsers)) {
    throw new Error(`${TOKEN_FILE}의 최상위 값은 배열이어야 합니다.`);
  }

  return parsedUsers;
});

if (testUsers.length < TARGET_VUS) {
  throw new Error(
    `사전 발급 토큰이 부족합니다. required=${TARGET_VUS}, actual=${testUsers.length}`,
  );
}

const pollIntervalMs = POLL_INTERVAL_SECONDS * 1000;
const rampUpMs = RAMP_UP_SECONDS * 1000;
const steadyEndMs = rampUpMs + STEADY_SECONDS * 1000;

const pollingRequests = new Counter("polling_requests");
const pollingFailures = new Rate("polling_failed");
const pollingDuration = new Trend("polling_duration", true);
const pollingWaiting = new Trend("polling_waiting", true);

let nextPollAtMs = null;

export const options = {
  scenarios: {
    unreadCountPollingLimit: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        {
          duration: `${RAMP_UP_SECONDS}s`,
          target: TARGET_VUS,
        },
        {
          duration: `${STEADY_SECONDS}s`,
          target: TARGET_VUS,
        },
        {
          duration: `${RAMP_DOWN_SECONDS}s`,
          target: 0,
        },
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    [`polling_requests{phase:steady,auth_mode:${AUTH_MODE}}`]: ["count>0"],
    [`polling_failed{phase:steady,auth_mode:${AUTH_MODE}}`]: ["rate<0.01"],
    [`polling_duration{phase:steady,auth_mode:${AUTH_MODE}}`]: [
      `p(95)<${P95_LIMIT_MS}`,
      `p(99)<${P99_LIMIT_MS}`,
    ],
    [`polling_waiting{phase:steady,auth_mode:${AUTH_MODE}}`]: [
      `p(95)<${P95_LIMIT_MS}`,
      `p(99)<${P99_LIMIT_MS}`,
    ],
    [`checks{scope:polling,phase:steady,auth_mode:${AUTH_MODE}}`]: [
      "rate>0.99",
    ],
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

export default function () {
  waitUntilNextPollingTime();

  const testUser = testUsers[exec.vu.idInTest - 1];

  if (
    !testUser
    || !Number.isInteger(testUser.userId)
    || typeof testUser.token !== "string"
    || testUser.token.length === 0
  ) {
    throw new Error(
      `VU ${exec.vu.idInTest}에 대응하는 토큰 데이터가 올바르지 않습니다.`,
    );
  }

  const phase = currentPhase();
  const tags = {
    scope: "polling",
    endpoint: "unread-count",
    name: "GET /notifications/unread-count",
    phase,
    auth_mode: AUTH_MODE,
  };
  const response = http.get(`${BASE_URL}/notifications/unread-count`, {
    headers: {
      Authorization: `Bearer ${testUser.token}`,
    },
    tags,
  });

  const validResponse =
    response.status === 200 &&
    typeof jsonValue(response, "data.unreadCount") === "number";

  check(
    response,
    {
      "미읽음 알림 개수 응답이 정상이다": () => validResponse,
    },
    tags,
  );

  pollingRequests.add(1, tags);
  pollingFailures.add(!validResponse, tags);
  pollingDuration.add(response.timings.duration, tags);
  pollingWaiting.add(response.timings.waiting, tags);

  moveToNextPollingTime();
}

function waitUntilNextPollingTime() {
  if (nextPollAtMs === null) {
    nextPollAtMs = Date.now() + Math.random() * pollIntervalMs;
  }

  const remainingMs = nextPollAtMs - Date.now();

  if (remainingMs > 0) {
    sleep(remainingMs / 1000);
  }
}

function moveToNextPollingTime() {
  nextPollAtMs += pollIntervalMs;

  if (nextPollAtMs <= Date.now()) {
    const missedTicks =
      Math.floor((Date.now() - nextPollAtMs) / pollIntervalMs) + 1;
    nextPollAtMs += missedTicks * pollIntervalMs;
  }
}

function currentPhase() {
  const elapsedMs = Date.now() - exec.scenario.startTime;

  if (elapsedMs < rampUpMs) {
    return "ramp-up";
  }

  if (elapsedMs < steadyEndMs) {
    return "steady";
  }

  return "ramp-down";
}

function jsonValue(response, selector) {
  try {
    return response.json(selector);
  } catch {
    return null;
  }
}

function numberFromEnvironment(name, defaultValue) {
  const rawValue = __ENV[name];
  const value = rawValue === undefined ? defaultValue : Number(rawValue);

  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }

  return value;
}
