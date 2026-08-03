import sse from "k6/x/sse";
import exec from "k6/execution";
import { SharedArray } from "k6/data";
import { check, sleep } from "k6";
import { setTimeout } from "k6/timers";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TARGET_VUS = numberFromEnvironment("TARGET_VUS", 1);
const RAMP_UP_SECONDS = numberFromEnvironment("RAMP_UP_SECONDS", 60);
const STEADY_SECONDS = numberFromEnvironment("STEADY_SECONDS", 120);
const RAMP_DOWN_SECONDS = numberFromEnvironment("RAMP_DOWN_SECONDS", 30);
const TOKEN_FILE = __ENV.TOKEN_FILE || "./.tokens/polling-users.json";

const connectionLifetimeMs = (RAMP_UP_SECONDS + STEADY_SECONDS) * 1000;
const rampUpEndMs = RAMP_UP_SECONDS * 1000;
const steadyEndMs = rampUpEndMs + STEADY_SECONDS * 1000;

const testUsers = new SharedArray("preissued SSE users", () => {
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

const sseConnections = new Counter("sse_connections");
const sseConnectionFailures = new Counter("sse_connection_failures");
const sseEventsReceived = new Counter("sse_events_received");
const sseEventFailures = new Counter("sse_event_failures");
const sseUnexpectedDisconnects = new Counter("sse_unexpected_disconnects");

export const options = {
  scenarios: {
    notificationSse: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: `${RAMP_UP_SECONDS}s`, target: TARGET_VUS },
        { duration: `${STEADY_SECONDS}s`, target: TARGET_VUS },
        { duration: `${RAMP_DOWN_SECONDS}s`, target: 0 },
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    "checks{scope:sse}": ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "min", "med", "max"],
};

export default function () {
  const testUser = testUsers[exec.vu.idInTest - 1];
  validateTestUser(testUser);

  const phase = currentPhase();
  const tags = {
    scope: "sse",
    endpoint: "subscribe",
    name: "GET /notifications/subscribe",
    phase,
  };
  const intentionalClose = { value: false };
  const connected = { value: false };

  const response = sse.open(
    `${BASE_URL}/notifications/subscribe`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${testUser.token}`,
        Accept: "text/event-stream",
        "Cache-Control": "no-cache",
      },
      tags,
    },
    (client) => {
      client.on("open", () => {
        connected.value = true;
        sseConnections.add(1, tags);

        check(
          { connected: true },
          {
            "SSE 연결 이벤트가 발생했다": (value) => value.connected,
          },
          tags,
        );

        const closeDelayMs = Math.max(
          0,
          connectionLifetimeMs - (Date.now() - exec.scenario.startTime),
        );
        setTimeout(() => {
          intentionalClose.value = true;
          client.close();
        }, closeDelayMs);
      });

      client.on("event", (event) => {
        const eventTags = {
          ...tags,
          event: event.name || "message",
        };
        const validEvent =
          event.name === "connect" || event.name === "notification";

        sseEventsReceived.add(1, eventTags);
        if (!validEvent) {
          sseEventFailures.add(1, eventTags);
        }

        check(
          event,
          {
            "SSE 이벤트 이름이 정상이다": () => validEvent,
          },
          eventTags,
        );
      });

      client.on("error", (error) => {
        if (!connected.value) {
          sseConnectionFailures.add(1, tags);
        }
        if (!intentionalClose.value) {
          sseUnexpectedDisconnects.add(1, tags);
        }
        console.debug(`SSE client error: ${error.error()}`);
      });
    },
  );

  const validResponse = response && response.status === 200;
  if (!connected.value) {
    check(
      response,
      {
        "SSE 연결 응답이 200이다": () => validResponse,
      },
      tags,
    );

    console.error(
      `SSE 연결 실패: status=${response?.status}, error=${response?.error}`,
    );
    sleep(1);
  }
}

function validateTestUser(testUser) {
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
}

function currentPhase() {
  const elapsedMs = Date.now() - exec.scenario.startTime;

  if (elapsedMs < rampUpEndMs) {
    return "ramp-up";
  }

  if (elapsedMs < steadyEndMs) {
    return "steady";
  }

  return "ramp-down";
}

function numberFromEnvironment(name, defaultValue) {
  const rawValue = __ENV[name];
  const value = rawValue === undefined ? defaultValue : Number(rawValue);

  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }

  return value;
}
