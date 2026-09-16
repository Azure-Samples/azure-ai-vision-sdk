import logging

from app.telemetry import app_insights_server


def test_attestation_telemetry_defaults_to_console_without_app_insights(monkeypatch, caplog):
    monkeypatch.delenv("APPLICATIONINSIGHTS_CONNECTION_STRING", raising=False)
    monkeypatch.setattr(app_insights_server, "_init_attempted", False)
    monkeypatch.setattr(app_insights_server, "_logger", None)

    with caplog.at_level(logging.INFO, logger="ai"):
        app_insights_server.track_event(
            "Test.Event",
            {"publicKey": "PUBLIC_KEY"},
            {"durationMs": 12},
        )
        app_insights_server.track_exception(
            ValueError("test failure"),
            {"source": "test"},
        )
        app_insights_server.track_dependency(
            "Test.Dependency",
            target="example.test",
            duration=7,
            success=True,
            result_code=200,
        )

    messages = [record.getMessage() for record in caplog.records]
    assert any("[Attestation] Event" in message and "PUBLIC_KEY" in message for message in messages)
    assert any("[Attestation] Exception" in message and "test failure" in message for message in messages)
    assert any("[Attestation] Dependency" in message and "example.test" in message for message in messages)