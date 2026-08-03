-- Agent iteration 7.3: allow confirmed cancellation of recent unpaid orders.
-- Run against zjzx_agent after 20260729_agent_action_request.sql.

ALTER TABLE agent_action_request
    DROP CONSTRAINT IF EXISTS ck_agent_action_type;

ALTER TABLE agent_action_request
    ADD CONSTRAINT ck_agent_action_type CHECK (
        action_type IN ('ADD_TO_CART', 'CANCEL_RECENT_ORDER')
    );
