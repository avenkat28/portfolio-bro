#!/usr/bin/env python3
"""Exercise a running local MVP. Creates and retains a demo portfolio with immutable trades."""
import json
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080"


def request(method, path, payload=None, key=None, expected=200):
    headers = {"Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
    if status != expected:
        raise AssertionError(f"{method} {path}: expected {expected}, received {status}: {body.decode()}")
    return json.loads(body) if body else None


def main():
    assert request("GET", "/actuator/health")["status"] == "UP"
    portfolio = request("POST", "/api/v1/portfolios", {"name": "Smoke test " + str(uuid.uuid4())[:8]}, expected=201)
    path = "/api/v1/portfolios/" + portfolio["id"]
    timestamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    def trade(side, shares, price, fees):
        return {"side": side, "ticker": "AAPL", "shares": shares,
                "pricePerShare": price, "fees": fees, "executedAt": timestamp}
    key = str(uuid.uuid4())
    buy = trade("BUY", "10", "100", "2")
    first = request("POST", path + "/trades", buy, key, 201)
    request("POST", path + "/trades", trade("BUY", "5", "120", "1"), str(uuid.uuid4()), 201)
    request("POST", path + "/trades", trade("SELL", "6", "130", "2"), str(uuid.uuid4()), 201)
    assert request("POST", path + "/trades", buy, key) == first
    request("POST", path + "/trades", trade("SELL", "10", "130", "0"), str(uuid.uuid4()), 409)
    holding = request("GET", path + "/holdings")["holdings"][0]
    assert holding["shares"] == "9" and holding["quote"]["source"] == "MOCK"
    result = request("GET", path + "/performance")
    assert result["valuationComplete"] is True
    assert result["totalPortfolioValue"] == "1125.00"
    assert result["realizedProfitLoss"] == "136.80"
    assert result["unrealizedProfitLoss"] == "163.20"
    assert result["totalProfitLoss"] == "300.00"
    assert request("GET", path + "/trades")["totalElements"] == 3
    request("DELETE", path, expected=409)
    print("PASS: health, trade recording, idempotency, oversell rejection, holdings, performance, and history protection")
    print("Demo portfolio retained: " + portfolio["id"])


if __name__ == "__main__":
    main()
