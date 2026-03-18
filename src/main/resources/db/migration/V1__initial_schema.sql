CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS contests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE',   -- ACTIVE, ENDED
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    contest_id UUID NOT NULL REFERENCES contests(id),
    score BIGINT NOT NULL,
    submitted_at TIMESTAMP DEFAULT NOW(),

    -- Only keep personal best per user per contest
    -- On conflict, update only if new score is higher
    UNIQUE(user_id, contest_id)
);

CREATE INDEX idx_scores_contest ON scores(contest_id);
CREATE INDEX idx_scores_user ON scores(user_id);
CREATE INDEX idx_scores_score ON scores(contest_id, score DESC);
CREATE INDEX idx_contests_status ON contests(status);
