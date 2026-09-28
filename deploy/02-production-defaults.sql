USE image_creator;
-- Runs only when the Docker database volume is first initialized.
INSERT INTO mail_config (dev_return_code)
SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM mail_config);
UPDATE mail_config SET dev_return_code = 0;
