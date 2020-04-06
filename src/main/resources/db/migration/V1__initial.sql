CREATE TABLE vedtak
(
    id               INT      NOT NULL PRIMARY KEY,
    fnr              CHAR(11) NOT NULL,
    vedtaksperiodeId CHAR(36) NOT NULL,
    fom              DATE     NOT NULL,
    tom              DATE     NOT NULL,
    grad             DECIMAL  NOT NULL
);
