CREATE TABLE equipment (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    name                VARCHAR(50)     NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    positioning_source  VARCHAR(20)     NOT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
