-- One run of the embeddings step at a time across all instances (specs/LAB-129, ADR-0017). A lease instead of a row
-- lock: nothing keeps a connection or a transaction open while the model is called, and an instance that dies loses the
-- lock when it expires. owner identifies the run that holds it; locked_until is always computed with now() of this
-- database, so the clocks of the instances do not matter. Searches and uploads never read this table.

CREATE TABLE indexing_lock (
    name         text        PRIMARY KEY,
    owner        text        NOT NULL CHECK (btrim(owner) <> ''),
    locked_until timestamptz NOT NULL
);
