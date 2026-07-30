import http from "k6/http";
import { check, fail, sleep } from "k6";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TARGET_VUS = numberFromEnvironment("TARGET_VUS", 10);
const POLL_INTERVAL_SECONDS = numberFromEnvironment(
  "POLL_INTERVAL_SECONDS",
  30,
);
const RAMP_UP_SECONDS = numberFromEnvironment("RAMP_UP_SECONDS", 30);
const STEADY_SECONDS = numberFromEnvironment("STEADY_SECONDS", 120);
const RAMP_DOWN_SECONDS = numberFromEnvironment("RAMP_DOWN_SECONDS", 30);
const P95_LIMIT_MS = numberFromEnvironment("P95_LIMIT_MS", 500);
const P99_LIMIT_MS = numberFromEnvironment("P99_LIMIT_MS", 1000);
const ACCOUNT_PREFIX = __ENV.ACCOUNT_PREFIX || "loadtest";
const ACCOUNT_DOMAIN = __ENV.ACCOUNT_DOMAIN || "example.com";
const PASSWORD = requiredEnvironment("LOAD_TEST_PASSWORD");

const pollIntervalMs = POLL_INTERVAL_SECONDS * 1000;
const rampUpMs = RAMP_UP_SECONDS * 1000;
const steadyEndMs = rampUpMs + STEADY_SECONDS * 1000;

const pollingRequests = new Counter("polling_requests");
const pollingFailures = new Rate("polling_failed");
const pollingDuration = new Trend("polling_duration", true);
const pollingWaiting = new Trend("polling_waiting", true);

let accessToken = null;
let tokenType = "Bearer";
let nextPollAtMs = null;

export const options = {
  scenarios: {
    unreadCountPolling: {
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
    "polling_requests{phase:steady}": ["count>0"],
    "polling_failed{phase:steady}": ["rate<0.01"],
    "polling_duration{phase:steady}": [
      `p(95)<${P95_LIMIT_MS}`,
      `p(99)<${P99_LIMIT_MS}`,
    ],
    "polling_waiting{phase:steady}": [
      `p(95)<${P95_LIMIT_MS}`,
      `p(99)<${P99_LIMIT_MS}`,
    ],
    "checks{scope:polling,phase:steady}": ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

export default function () {
  loginOncePerVirtualUser();
  waitUntilNextPollingTime();

  const phase = currentPhase();
  const response = http.get(`${BASE_URL}/notifications/unread-count`, {
    headers: {
      Authorization: `${tokenType} ${accessToken}`,
    },
    tags: {
      endpoint: "unread-count",
      name: "GET /notifications/unread-count",
      phase,
    },
  });

  const validResponse =
    response.status === 200 &&
    typeof jsonValue(response, "data.unreadCount") === "number";

  check(
    response,
    {
      "미읽음 알림 개수 응답이 정상이다": () => validResponse,
    },
    {
      scope: "polling",
      phase,
    },
  );

  pollingRequests.add(1, { phase });
  pollingFailures.add(!validResponse, { phase });
  pollingDuration.add(response.timings.duration, { phase });
  pollingWaiting.add(response.timings.waiting, { phase });

  moveToNextPollingTime();
}

function loginOncePerVirtualUser() {
  if (accessToken !== null) {
    return;
  }

  const accountNumber = (__VU - 1) % TARGET_VUS + 1;
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({
      email: `${ACCOUNT_PREFIX}${accountNumber}@${ACCOUNT_DOMAIN}`,
      password: PASSWORD,
    }),
    {
      headers: {
        "Content-Type": "application/json",
      },
      tags: {
        endpoint: "login",
        name: "POST /auth/login",
        phase: currentPhase(),
      },
    },
  );

  const issuedAccessToken = jsonValue(response, "data.accessToken");
  const issuedTokenType = jsonValue(response, "data.tokenType");
  const loggedIn = response.status === 200 && Boolean(issuedAccessToken);

  check(
    response,
    {
      "가상 사용자 로그인이 성공했다": () => loggedIn,
    },
    {
      scope: "login",
    },
  );

  if (!loggedIn) {
    fail(
      `VU ${__VU} 로그인 실패: status=${response.status}, body=${response.body}`,
    );
  }

  accessToken = issuedAccessToken;
  tokenType = issuedTokenType || "Bearer";
}

function waitUntilNextPollingTime() {
  if (nextPollAtMs === null) {
    nextPollAtMs = Date.now() + Math.random() * pollIntervalMs; // 사용자마다 첫 요청 무작위로 지연
  }

  const remainingMs = nextPollAtMs - Date.now();

  if (remainingMs > 0) {
    sleep(remainingMs / 1000);
  }
}

function moveToNextPollingTime() {
  nextPollAtMs += pollIntervalMs;

  // 이전 요청이 폴링 주기보다 오래 걸렸다면 지나간 tick을 건너뛴다.
  if (nextPollAtMs <= Date.now()) {
    const missedTicks =
      Math.floor((Date.now() - nextPollAtMs) / pollIntervalMs) + 1;
    nextPollAtMs += missedTicks * pollIntervalMs;
  }
}

function currentPhase() {
  const elapsedMs = exec.instance.currentTestRunDuration;

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

function requiredEnvironment(name) {
  const value = __ENV[name];

  if (!value) {
    throw new Error(`${name} 환경변수가 필요합니다.`);
  }

  return value;
}

function numberFromEnvironment(name, defaultValue) {
  const rawValue = __ENV[name];
  const value = rawValue === undefined ? defaultValue : Number(rawValue);

  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }

  return value;
}
