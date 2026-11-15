CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    description TEXT NOT NULL DEFAULT '',
    category_id BIGINT NOT NULL REFERENCES categories(id),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status VARCHAR(15) NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    CHECK ((status IN ('NEW', 'IN_PROGRESS') AND closed_at IS NULL)
        OR (status IN ('RESOLVED', 'CANCELLED') AND closed_at IS NOT NULL))
);
CREATE TABLE status_history (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    from_status VARCHAR(15) CHECK (from_status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    to_status VARCHAR(15) NOT NULL CHECK (to_status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_tickets_created ON tickets(created_at, id);
CREATE INDEX idx_tickets_status_due ON tickets(status, due_at);
CREATE INDEX idx_tickets_category_priority ON tickets(category_id, priority);
CREATE INDEX idx_history_ticket ON status_history(ticket_id, id);
