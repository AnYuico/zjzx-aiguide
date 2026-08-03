-- BCrypt hashes are 60 characters. The original sys_user.password column was
-- varchar(32), which only fits the legacy MD5 hash.
ALTER TABLE sys_user
    MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'password hash';

-- user_info.password is already varchar(500) in the current data dictionary.
