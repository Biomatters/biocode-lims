CREATE TABLE users (
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  firstname VARCHAR(255) NOT NULL,
  lastname VARCHAR(255) NOT NULL,
  email VARCHAR(320) NOT NULL,
  enabled BIT NOT NULL,
  PRIMARY KEY(username)
) ENGINE = INNODB;

CREATE TABLE authorities (
  username  VARCHAR(255) NOT NULL,
  authority VARCHAR(50)  NOT NULL,
  PRIMARY KEY (username),
  FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
) ENGINE = INNODB;

CREATE TABLE project (
  id INT NOT NULL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  external_id VARCHAR(255),
  description VARCHAR(255),
  parent INT REFERENCES project(id) ON DELETE CASCADE
) ENGINE=INNODB;

CREATE TABLE project_role (
  project_id INT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  username VARCHAR(255) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
  role INT,
  PRIMARY KEY(project_id, username)
) ENGINE=INNODB;






