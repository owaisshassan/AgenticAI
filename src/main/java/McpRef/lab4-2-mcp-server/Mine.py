import json
from urllib.parse import urlencode

TOOLS = [
    {"name": "search_tickets",
     "description": ("Find support tickets by status, priority, account or exact words in the "
                     "title/body. Returns a short list (id, title, status, priority, account_id, "
                     "assignee, updated_at). Use this whenever you do not already have a ticket id; "
                     "use get_ticket for the full body and comments."),
     "input_schema": {"type": "object", "properties": {
         "status": {"type": "string", "enum": ["open", "in_progress", "resolved", "closed"]},
         "priority": {"type": "string", "enum": ["P1", "P2", "P3", "P4"], "description": "P1 is most urgent",
                      "examples": ["P1"]},
         "account_id": {"type": "string", "pattern": "^ACC-\\d{4}$", "description": "e.g. ACC-1001",
                        "examples": ["ACC-1001"]},
         "keywords": {"type": "string", "maxLength": 200,
                      "description": "A word or exact phrase to match in the title or body, e.g. 'DICOM'. "
                                     "Matched literally, not split into separate OR'd words."},
         "limit": {"type": "integer", "minimum": 1, "maximum": 50,
                   "description": "Max tickets to return (default 20, hard cap 50)."}},
                      "additionalProperties": False}},
    {"name": "get_ticket",
     "description": ("Full ticket by id: title, body, status, priority, assignee and every comment "
                     "(resolutions are recorded as comments)."),
     "input_schema": {"type": "object", "properties": {
         "id": {"type": "string", "pattern": "^T-\\d{4}$", "description": "Ticket id, e.g. T-1001",
                "examples": ["T-1001"]}},
                      "required": ["id"], "additionalProperties": False}},
    {"name": "get_account",
     "description": ("Customer account by id: name, tier, region, contracted SLA in minutes, and the "
                     "number of open tickets. Ids look like ACC-1001; this does not search by name."),
     "input_schema": {"type": "object", "properties": {
         "id": {"type": "string", "pattern": "^ACC-\\d{4}$", "description": "e.g. ACC-1001",
                "examples": ["ACC-1001"]}},
                      "required": ["id"], "additionalProperties": False}},
    {"name": "get_config",
     "description": ("Read platform configuration. Pass a key (e.g. 'ingest.rush_slide_limit') for one "
                     "value and its description, or omit key to list every key with its value and "
                     "description - use this to discover what exists instead of guessing a key name."),
     "input_schema": {"type": "object", "properties": {
         "key": {"type": "string", "maxLength": 100, "description": "Dotted key, e.g. alerts.ingest_latency_minutes",
                 "examples": ["alerts.ingest_latency_minutes"]}},
                      "additionalProperties": False}},
]


def run(api, name, args):
    """Same API as BAD. Errors pass through with their code, message and hint -
    the one-line fix (drop the try/except-to-"error" pattern) that mattered most."""
    if name == "search_tickets":
        params = {k: v for k, v in (
            ("status", args.get("status")),
            ("priority", args.get("priority")),
            ("account_id", args.get("account_id")),
            ("q", args.get("keywords")),
            ("limit", args.get("limit")),
        ) if v}
        s, b = api("GET", "/tickets?" + urlencode(params))
    elif name == "get_ticket":
        s, b = api("GET", f"/tickets/{args.get('id', '')}")
    elif name == "get_account":
        s, b = api("GET", f"/accounts/{args.get('id', '')}")
    elif name == "get_config":
        key = args.get("key")
        s, b = api("GET", f"/config/{key}" if key else "/config")
    else:
        return (json.dumps({"error": {"code": "unknown_tool", "message": name,
                                      "hint": "Available: " + ", ".join(t["name"] for t in TOOLS)}}), True)
    return json.dumps(b), s >= 400
