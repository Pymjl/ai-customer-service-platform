UPDATE cs_user
SET password = '{bcrypt}$2a$10$HkDfIs0EoLpkeOIdFp1RO.JEY6Zi9HLG0nNCeKww6PyJlYjkk5WYC',
    updated_at = CURRENT_TIMESTAMP
WHERE password = '{noop}password';

UPDATE cs_user
SET password = '{bcrypt}$2a$10$/9CzlkQ/47faFLvur.QZQ.yXE4gtJ39wGicp9dzxBAPu6mrQ83czq',
    updated_at = CURRENT_TIMESTAMP
WHERE password = '{noop}admin123';
