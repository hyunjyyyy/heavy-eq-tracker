CREATE TABLE site (
                      id          BIGINT       NOT NULL AUTO_INCREMENT,
                      name        VARCHAR(50)  NOT NULL,
                      created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_site_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO site (id, name) VALUES (1, 'default-site');
