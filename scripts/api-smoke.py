#!/usr/bin/env python3
"""Gayadi 서버 HTTP 스모크 테스트.

PostgreSQL에 붙은 로컬 서버에서 토큰 발급부터 여행·일정·경비·경로까지 직접 호출한다.

사용 예:

    python3 scripts/api-smoke.py
    python3 scripts/api-smoke.py --base-url http://127.0.0.1:8080
    python3 scripts/api-smoke.py --email owner@example.com --password password1

기본은 매번 새 계정을 만든다. 기존 계정을 쓰려면 --email / --password 를 넘긴다.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from typing import Any


class ApiClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.results: list[tuple[bool, str, str, str, int, str]] = []

    def call(
        self,
        name: str,
        method: str,
        path: str,
        expected: set[int],
        token: str | None = None,
        payload: Any = None,
        query: dict[str, Any] | None = None,
    ) -> Any:
        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(
                {key: value for key, value in query.items() if value is not None}
            )
        headers = {
            "Accept": "application/json",
            "Accept-Language": "ko",
        }
        data = None
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read().decode("utf-8")
                status = response.status
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8")
            status = error.code
        except Exception as error:
            self._record(False, name, method, path, 0, str(error))
            return None
        body: Any = raw
        if raw:
            try:
                body = json.loads(raw)
            except json.JSONDecodeError:
                body = raw
        ok = status in expected
        snippet = redact_for_log(body)
        if isinstance(snippet, (dict, list)):
            snippet = json.dumps(snippet, ensure_ascii=False)
        if isinstance(snippet, str) and len(snippet) > 180:
            snippet = snippet[:180] + "..."
        self._record(ok, name, method, path, status, str(snippet))
        return body

    def _record(
        self, ok: bool, name: str, method: str, path: str, status: int, snippet: str
    ) -> None:
        self.results.append((ok, name, method, path, status, snippet))
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {status:>3} {method:6} {path}  {name}")
        if not ok:
            print(f"       {snippet}")


def redact_for_log(value: Any) -> Any:
    if isinstance(value, dict):
        redacted: dict[str, Any] = {}
        for key, item in value.items():
            normalized = key.lower().replace("_", "").replace("-", "")
            if normalized.endswith("token") or normalized in {"email", "invitecode"}:
                redacted[key] = "[REDACTED]"
            else:
                redacted[key] = redact_for_log(item)
        return redacted
    if isinstance(value, list):
        return [redact_for_log(item) for item in value]
    return value


def register_or_login(
    client: ApiClient, email: str, password: str, nickname: str
) -> tuple[str, int]:
    signup = client.call(
        f"register {nickname}",
        "POST",
        "/api/v1/auth/registrations",
        {201, 409},
        payload={"email": email, "password": password, "nickname": nickname},
    )
    login = client.call(
        f"login {nickname}",
        "POST",
        "/api/v1/auth/tokens",
        {200},
        payload={"email": email, "password": password},
    )
    if not isinstance(login, dict) or "accessToken" not in login:
        raise SystemExit("토큰 발급 실패")
    token = login["accessToken"]
    user = client.call(f"me {nickname}", "GET", "/api/v1/users/current", {200}, token=token)
    user_id = user["id"] if isinstance(user, dict) else login.get("user", {}).get("id")
    if user_id is None and isinstance(signup, dict):
        user_id = signup.get("user", {}).get("id")
    if user_id is None:
        raise SystemExit("사용자 ID를 읽지 못했습니다")
    return token, int(user_id)


def survey_answers(survey: Any) -> list[dict[str, str]]:
    questions = survey.get("questions", []) if isinstance(survey, dict) else []
    answers = []
    for question in questions:
        options = question.get("options") or []
        if options:
            answers.append({"questionId": question["id"], "optionId": options[0]["id"]})
    if not answers:
        answers = [{"questionId": f"q0{i}", "optionId": "a"} for i in range(1, 10)]
    return answers


def run(args: argparse.Namespace) -> int:
    client = ApiClient(args.base_url)
    stamp = str(int(time.time()))[-7:]
    owner_email = args.email or f"smoke-owner-{stamp}@example.com"
    member_email = args.member_email or f"smoke-member-{stamp}@example.com"
    password = args.password

    client.call("health", "GET", "/actuator/health", {200})
    client.call("openapi", "GET", "/api/openapi", {200})
    survey = client.call("survey", "GET", "/api/v1/surveys/travel-personality-v1", {200})
    client.call("places", "GET", "/api/v1/places", {200})
    client.call("notices", "GET", "/api/v1/notices", {200})
    client.call("terms", "GET", "/api/v1/legal-documents/terms-of-service", {200})
    client.call("trips-unauth", "GET", "/api/v1/trips", {401})

    owner_token, owner_id = register_or_login(client, owner_email, password, args.owner_nickname)
    member_token, member_id = register_or_login(
        client, member_email, password, args.member_nickname
    )
    print(f"OWNER_ID={owner_id}")
    print(f"MEMBER_ID={member_id}")

    answers = survey_answers(survey)
    client.call(
        "survey-submit",
        "POST",
        "/api/v1/surveys/travel-personality-v1/submissions",
        {201},
        token=owner_token,
        payload={"answers": answers},
    )
    client.call(
        "survey-submit-member",
        "POST",
        "/api/v1/surveys/travel-personality-v1/submissions",
        {201},
        token=member_token,
        payload={"answers": answers},
    )

    start = date.today() + timedelta(days=14)
    end = start + timedelta(days=1)
    trip = client.call(
        "create-trip",
        "POST",
        "/api/v1/trips",
        {201},
        token=owner_token,
        payload={
            "name": args.trip_name,
            "startDate": start.isoformat(),
            "endDate": end.isoformat(),
            "cities": ["서울"],
        },
    )
    if not isinstance(trip, dict) or "id" not in trip:
        print("\n여행 생성 실패. 여기까지 결과만 보고합니다.")
        return summarize(client)
    trip_id = trip["id"]
    invite_code = trip.get("inviteCode")
    print(f"TRIP_ID={trip_id}")
    prefix = f"/api/v1/trips/{trip_id}"

    client.call("list-trips", "GET", "/api/v1/trips", {200}, token=owner_token)
    client.call("get-trip", "GET", prefix, {200}, token=owner_token)
    client.call(
        "join-trip",
        "POST",
        "/api/v1/trip-memberships",
        {201},
        token=member_token,
        payload={
            "inviteCode": invite_code,
            "departurePlaceId": 1,
            "returnPlaceId": 4,
        },
    )
    client.call("participants", "GET", f"{prefix}/participants", {200}, token=owner_token)
    client.call("invitations", "GET", f"{prefix}/invitations", {200}, token=owner_token)

    client.call(
        "date-owner",
        "PUT",
        f"{prefix}/date-coordination/availability/current",
        {200},
        token=owner_token,
        payload={"dates": [start.isoformat(), end.isoformat()]},
    )
    client.call(
        "date-member",
        "PUT",
        f"{prefix}/date-coordination/availability/current",
        {200},
        token=member_token,
        payload={"dates": [start.isoformat(), end.isoformat()]},
    )
    client.call(
        "finalize-dates",
        "PUT",
        f"{prefix}/date-coordination/finalized-dates",
        {200},
        token=owner_token,
        payload={"startDate": start.isoformat(), "endDate": end.isoformat()},
    )

    client.call(
        "trip-survey",
        "POST",
        f"{prefix}/survey-responses",
        {201},
        token=owner_token,
        payload={"answers": answers},
    )
    client.call(
        "trip-survey-member",
        "POST",
        f"{prefix}/survey-responses",
        {201},
        token=member_token,
        payload={"answers": answers},
    )
    client.call("personality", "GET", f"{prefix}/personality-profile", {200}, token=owner_token)
    client.call("generate-plan", "POST", f"{prefix}/plans", {201}, token=owner_token)
    schedules = client.call("schedules", "GET", f"{prefix}/schedules", {200}, token=owner_token)
    if isinstance(schedules, list) and schedules:
        schedule_ids = [item["id"] for item in schedules]
        client.call(
            "reorder",
            "PATCH",
            f"{prefix}/schedule-orders",
            {200},
            token=owner_token,
            payload={"scheduleIds": schedule_ids},
        )
        first_schedule = schedules[0]["id"]
    else:
        created = client.call(
            "create-schedule",
            "POST",
            f"{prefix}/schedules",
            {201},
            token=owner_token,
            payload={
                "title": "서울숲",
                "date": start.isoformat(),
                "time": "10:00",
                "endTime": "12:00",
                "type": "MAIN",
                "placeId": 1,
            },
        )
        first_schedule = created.get("id") if isinstance(created, dict) else None

    client.call("dashboard", "GET", f"{prefix}/dashboard", {200}, token=owner_token)
    client.call(
        "shared-fund",
        "POST",
        f"{prefix}/shared-fund/contributions",
        {201},
        token=owner_token,
        payload={"amount": 50000},
    )
    client.call(
        "expense",
        "POST",
        f"{prefix}/expenses",
        {201},
        token=owner_token,
        payload={
            "title": "점심",
            "memo": "스모크 테스트",
            "amount": 12000,
            "payerId": owner_id,
            "participantIds": [owner_id, member_id],
            "date": start.isoformat(),
            "time": "12:30",
            "category": "FOOD",
            "paymentSource": "PERSONAL",
            "scheduleId": first_schedule,
        },
    )
    client.call("expenses", "GET", f"{prefix}/expenses", {200}, token=owner_token)
    client.call("settlement", "GET", f"{prefix}/expense-settlement", {200}, token=owner_token)
    client.call("fund-get", "GET", f"{prefix}/shared-fund", {200}, token=owner_token)

    client.call(
        "favorite",
        "PUT",
        "/api/v1/users/current/favorite-places/1",
        {200},
        token=owner_token,
        payload={"memo": "스모크"},
    )
    client.call("favorites", "GET", "/api/v1/users/current/favorite-places", {200}, token=owner_token)

    itinerary = client.call(
        "route-itinerary",
        "POST",
        f"{prefix}/route-recommendations",
        {201, 400},
        token=owner_token,
        payload={"type": "ITINERARY"},
    )
    if isinstance(itinerary, dict) and itinerary.get("id"):
        route_type = itinerary.get("type") or "ITINERARY"
        client.call(
            "select-route",
            "PUT",
            f"{prefix}/route-selections/{route_type}",
            {200},
            token=owner_token,
            payload={"routeId": itinerary["id"]},
        )
    client.call(
        "route-departure-member",
        "POST",
        f"{prefix}/route-recommendations",
        {201, 400},
        token=member_token,
        payload={"type": "DEPARTURE", "userId": member_id},
    )

    client.call(
        "start-trip",
        "PATCH",
        f"{prefix}/status",
        {200},
        token=owner_token,
        payload={"status": "IN_PROGRESS"},
    )
    client.call(
        "event",
        "POST",
        f"{prefix}/event-observations",
        {201},
        token=owner_token,
        payload={
            "placeId": 1,
            "eventType": "WEATHER",
            "source": "SMOKE",
            "severity": "HIGH",
            "values": {"rainfallMm": 12},
        },
    )
    client.call("proposals", "GET", f"{prefix}/change-proposals", {200}, token=owner_token)
    client.call(
        "complete-trip",
        "PATCH",
        f"{prefix}/status",
        {200, 400, 409},
        token=owner_token,
        payload={"status": "COMPLETED"},
    )
    return summarize(client)


def summarize(client: ApiClient) -> int:
    failed = [item for item in client.results if not item[0]]
    print("\n==== SUMMARY ====")
    print(f"total={len(client.results)} pass={len(client.results) - len(failed)} fail={len(failed)}")
    if failed:
        print("FAILED:")
        for _, name, method, path, status, snippet in failed:
            print(f"  {status} {method} {path} {name} {snippet}")
        return 1
    print("DB에 붙은 서버에서 토큰 발급부터 핵심 API까지 통과했습니다.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Gayadi API 스모크 테스트")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--email", help="기존 소유자 이메일. 없으면 새 계정을 만듭니다.")
    parser.add_argument("--member-email", help="기존 참여자 이메일. 없으면 새 계정을 만듭니다.")
    parser.add_argument("--password", default="password1")
    parser.add_argument("--owner-nickname", default="스모크장")
    parser.add_argument("--member-nickname", default="스모크원")
    parser.add_argument("--trip-name", default="스모크 여행")
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
