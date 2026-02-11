UPDATE user_account
SET email_verified = 1
WHERE email_verified IS NULL OR email_verified = 0;