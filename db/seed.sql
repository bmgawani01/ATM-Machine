-- Demo data, safe to run more than once.
-- Credentials to use on the login screen:  card no  1234567890   pin  1234

USE bankmanagementsystem_db;

INSERT IGNORE INTO login (cardno, pin) VALUES ('1234567890', '1234');
INSERT IGNORE INTO login (cardno, pin) VALUES ('9999888877', '1111');

INSERT INTO bank (pin, date, mode, amount)
SELECT '1234', 'Mon Jan 01 10:00:00 UTC 2024', 'Deposit', '25000'
  FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM bank WHERE pin = '1234' AND amount = '25000');

INSERT INTO bank (pin, date, mode, amount)
SELECT '1234', 'Tue Jan 02 11:30:00 UTC 2024', 'Withdrawal', '5000'
  FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM bank WHERE pin = '1234' AND amount = '5000');

INSERT INTO bank (pin, date, mode, amount)
SELECT '1111', 'Wed Jan 03 09:15:00 UTC 2024', 'Deposit', '8000'
  FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM bank WHERE pin = '1111' AND amount = '8000');

SELECT cardno, '******' AS pin FROM login;
SELECT * FROM bank;
