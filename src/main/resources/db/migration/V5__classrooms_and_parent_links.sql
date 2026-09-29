CREATE TABLE academic_years (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_academic_year_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classrooms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    academic_year_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    grade_level TINYINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_classroom_name_year UNIQUE (academic_year_id, name),
    CONSTRAINT fk_class_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_teachers (
    classroom_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    is_homeroom_teacher BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (classroom_id, teacher_id),
    CONSTRAINT fk_class_teacher_class FOREIGN KEY (classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_class_teacher_user FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_enrollments (
    classroom_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    enrolled_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (classroom_id, student_id),
    CONSTRAINT fk_enrollment_class FOREIGN KEY (classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE parent_link_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_parent_link_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_link_code_student FOREIGN KEY (student_id) REFERENCES users(id),
    INDEX idx_link_code_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE parent_student_links (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confirmed_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_parent_student UNIQUE (parent_id, student_id),
    CONSTRAINT fk_parent_link_parent FOREIGN KEY (parent_id) REFERENCES users(id),
    CONSTRAINT fk_parent_link_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT chk_parent_link_status CHECK (status IN ('PENDING', 'VERIFIED', 'REVOKED')),
    INDEX idx_parent_link_student (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO academic_years (name, starts_on, ends_on, active) VALUES ('2026-2027', '2026-08-15', '2027-05-31', TRUE);
INSERT INTO classrooms (academic_year_id, name, grade_level) VALUES ((SELECT id FROM academic_years WHERE name = '2026-2027'), '8A1', 8);
INSERT INTO class_teachers (classroom_id, teacher_id, is_homeroom_teacher) VALUES
((SELECT id FROM classrooms WHERE name = '8A1'), (SELECT id FROM users WHERE username = 'giaovien'), TRUE);
INSERT INTO class_enrollments (classroom_id, student_id) VALUES
((SELECT id FROM classrooms WHERE name = '8A1'), (SELECT id FROM users WHERE username = 'hocsinh'));
