-- Preserve all existing likes as LIKE; one reaction per member/post remains enforced.
ALTER TABLE community_likes
    ADD COLUMN reaction_type VARCHAR(8) NOT NULL DEFAULT 'LIKE',
    ADD CONSTRAINT chk_community_reaction_type
        CHECK (reaction_type IN ('LIKE','LOVE','CARE','HAHA','WOW','SAD','ANGRY'));