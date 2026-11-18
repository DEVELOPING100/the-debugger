-- LabLine database schema (PostgreSQL)
-- The Debugger team, CS2043, Sprint 1 (agreed Nov 5, 2026)
-- Decisions: one role per user; categories in a table;
-- estimated wait calculated (not stored); reprioritize in Sprint 2.

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) UNIQUE NOT NULL CHECK (email LIKE '%@unb.ca'),
  name          VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT'
                CHECK (role IN ('STUDENT','TA','INSTRUCTOR','ADMIN')),
  created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE courses (
  id            BIGSERIAL PRIMARY KEY,
  code          VARCHAR(20)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  instructor_id BIGINT       NOT NULL REFERENCES users(id)
);

CREATE TABLE lab_sessions (
  id          BIGSERIAL PRIMARY KEY,
  course_id   BIGINT       NOT NULL REFERENCES courses(id),
  name        VARCHAR(100) NOT NULL,
  room        VARCHAR(50),
  start_time  TIMESTAMP    NOT NULL,
  end_time    TIMESTAMP    NOT NULL,
  join_code   VARCHAR(10)  UNIQUE NOT NULL,
  queue_open  BOOLEAN      NOT NULL DEFAULT FALSE,
  CHECK (end_time > start_time)
);

CREATE TABLE categories (
  id        BIGSERIAL PRIMARY KEY,
  course_id BIGINT      NOT NULL REFERENCES courses(id),
  name      VARCHAR(50) NOT NULL,
  active    BOOLEAN     NOT NULL DEFAULT TRUE,
  UNIQUE (course_id, name)
);

CREATE TABLE session_tas (
  session_id BIGINT REFERENCES lab_sessions(id) ON DELETE CASCADE,
  ta_id      BIGINT REFERENCES users(id),
  PRIMARY KEY (session_id, ta_id)
);

CREATE TABLE help_requests (
  id            BIGSERIAL PRIMARY KEY,
  session_id    BIGINT       NOT NULL REFERENCES lab_sessions(id),
  student_id    BIGINT       NOT NULL REFERENCES users(id),
  category_id   BIGINT       NOT NULL REFERENCES categories(id),
  description   VARCHAR(300) NOT NULL,
  seat          VARCHAR(20)  NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'WAITING'
                CHECK (status IN ('WAITING','CLAIMED','DEFERRED','RESOLVED','CANCELLED')),
  claimed_by    BIGINT       REFERENCES users(id),
  time_joined   TIMESTAMP    NOT NULL DEFAULT NOW(),
  time_claimed  TIMESTAMP,
  time_resolved TIMESTAMP,
  rating        INTEGER CHECK (rating BETWEEN 1 AND 5),
  rating_comment VARCHAR(300)
);

-- A student can have only one open request per session
CREATE UNIQUE INDEX one_open_request_per_student
  ON help_requests (session_id, student_id)
  WHERE status IN ('WAITING','CLAIMED','DEFERRED');

CREATE TABLE audit_log (
  id         BIGSERIAL PRIMARY KEY,
  request_id BIGINT      NOT NULL REFERENCES help_requests(id),
  old_status VARCHAR(20),
  new_status VARCHAR(20) NOT NULL,
  changed_by BIGINT      NOT NULL REFERENCES users(id),
  changed_at TIMESTAMP   NOT NULL DEFAULT NOW(),
  reason     VARCHAR(255)
);

CREATE TABLE faq_entries (
  id         BIGSERIAL PRIMARY KEY,
  session_id BIGINT        NOT NULL REFERENCES lab_sessions(id),
  created_by BIGINT        NOT NULL REFERENCES users(id),
  question   VARCHAR(300)  NOT NULL,
  answer     VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);