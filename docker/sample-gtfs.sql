-- Small GTFS sample for local development (compose). Not the real SMTR feed.
-- Two bus lines with ida/volta shapes, one BRT (excluded by default), plus feed_info.

CREATE TABLE IF NOT EXISTS routes (
    route_id         VARCHAR PRIMARY KEY,
    route_short_name VARCHAR,
    route_long_name  VARCHAR,
    route_type       INTEGER,
    agency_id        VARCHAR
);

CREATE TABLE IF NOT EXISTS trips (
    trip_id       VARCHAR PRIMARY KEY,
    route_id      VARCHAR,
    service_id    VARCHAR,
    trip_headsign VARCHAR,
    direction_id  INTEGER,
    shape_id      VARCHAR
);

CREATE TABLE IF NOT EXISTS shapes (
    shape_id          VARCHAR,
    shape_pt_lat      DOUBLE PRECISION,
    shape_pt_lon      DOUBLE PRECISION,
    shape_pt_sequence INTEGER,
    PRIMARY KEY (shape_id, shape_pt_sequence)
);

CREATE TABLE IF NOT EXISTS feed_info (
    feed_publisher_name VARCHAR PRIMARY KEY,
    feed_version        VARCHAR,
    feed_start_date     DATE
);

INSERT INTO routes VALUES
    ('R100', '100', 'Rodoviaria <-> Praca XV', 3, 'SPPO'),
    ('R485', '485', 'Gavea <-> Central',       3, 'SPPO'),
    ('RBRT', 'BRT1', 'Transoeste',             3, 'BRT')
ON CONFLICT DO NOTHING;

INSERT INTO trips VALUES
    ('T1', 'R100', 'WKD', 'Praca XV',   0, 'S100_0'),
    ('T2', 'R100', 'WKD', 'Praca XV',   0, 'S100_0'),
    ('T3', 'R100', 'WKD', 'Rodoviaria', 1, 'S100_1'),
    ('T4', 'R485', 'WKD', 'Central',    0, 'S485_0'),
    ('T5', 'R485', 'WKD', 'Gavea',      1, 'S485_1'),
    ('T6', 'RBRT', 'WKD', 'Alvorada',   0, 'SBRT_0')
ON CONFLICT DO NOTHING;

INSERT INTO shapes VALUES
    ('S100_0', -22.9000, -43.2000, 1),
    ('S100_0', -22.9100, -43.2000, 2),
    ('S100_0', -22.9200, -43.1900, 3),
    ('S100_1', -22.9200, -43.1900, 1),
    ('S100_1', -22.9100, -43.2000, 2),
    ('S100_1', -22.9000, -43.2000, 3),
    ('S485_0', -22.8000, -43.3000, 1),
    ('S485_0', -22.8100, -43.3100, 2),
    ('S485_1', -22.8100, -43.3100, 1),
    ('S485_1', -22.8000, -43.3000, 2),
    ('SBRT_0', -22.9500, -43.4000, 1),
    ('SBRT_0', -22.9600, -43.4100, 2)
ON CONFLICT DO NOTHING;

INSERT INTO feed_info VALUES
    ('SMTR', '2026-07', DATE '2026-07-01')
ON CONFLICT DO NOTHING;
