-- End-to-end check of the exact SQL the screens run (Login, BalanceEnquiry,
-- FastCash, Deposit, MiniStatement, Pin, Signup2). Run it against the database
-- the app is configured for.  Safe to run: it only selects, plus one insert
-- and one update it rolls back.
USE bankmanagementsystem_db;

SELECT '1. Login screen' AS step;
SELECT cardno FROM login WHERE cardno = '1234567890' AND pin = '1234';

SELECT '2. Balance for pin 1234 (expect 20000)' AS step;
SELECT SUM(IF(mode = 'Deposit', CAST(amount AS SIGNED), -CAST(amount AS SIGNED))) AS balance
  FROM bank WHERE pin = '1234';

SELECT '3. Mini statement rows' AS step;
SELECT pin, date, mode, amount FROM bank WHERE pin = '1234';

SELECT '4. Card masking (MiniStatement needs 16 chars)' AS step;
SELECT CONCAT(SUBSTRING(cardno, 1, 4), 'XXXXXXXX', SUBSTRING(cardno, 13)) AS masked
  FROM login WHERE pin = '1234';

SELECT '5. Signup2 insert (Signup page 2)' AS step;
START TRANSACTION;
INSERT INTO signup2 VALUES ('TEST01', 'Hindu', 'General', 'Null', 'Graduate',
                           'Student', 'ABCDE1234F', '123456789012', 'No', 'No');
SELECT formno, religion, occupation FROM signup2 WHERE formno = 'TEST01';
ROLLBACK;

SELECT '6. Pin change (Pin screen updates all three tables)' AS step;
START TRANSACTION;
UPDATE bank SET pin = '1234' WHERE pin = '1234';
UPDATE login SET pin = '1234' WHERE pin = '1234';
UPDATE signupthree SET pin = '1234' WHERE pin = '1234';
ROLLBACK;

SELECT '7. Deposit insert shape (4 values, no column list)' AS step;
START TRANSACTION;
INSERT INTO bank VALUES ('1234', 'now', 'Deposit', '100');
SELECT COUNT(*) AS rows_for_1234 FROM bank WHERE pin = '1234';
ROLLBACK;

SELECT 'ALL CHECKS RAN' AS done;
