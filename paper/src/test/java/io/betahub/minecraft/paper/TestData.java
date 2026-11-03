package io.betahub.minecraft.paper;

final class TestData {

    static final String VERIFY_OK = """
            {"valid": true, "token_type": "project_auth_token",
             "project": {"id": 5, "name": "Network", "slug": "pr-0690627851"},
             "permissions": {"can_create_bug_report": true, "can_create_feature_request": false,
                             "can_create_ticket": false, "can_read_release_list": false, "can_create_release": true},
             "rate_limits": {"bug_reports_per_day": 500, "feature_requests_per_day": 8, "tickets_per_day": 8},
             "token_name": "Minecraft"}""";

    static final String CREATED = """
            {"id": 9001, "scoped_id": 42, "url": "https://app.betahub.io/projects/pr-0690627851/issues/42"}""";

    private TestData() {
    }
}
