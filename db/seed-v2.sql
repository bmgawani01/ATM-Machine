-- ATM System - reference data, version 2
--
-- The minimum a working installation needs: the billers customers can pay, the notes the
-- machine holds, the staff account, and the two demo customers used by the sign-in screen.
--
-- Loaded automatically on start, so this file is for building a database by hand or for
-- restoring a known-good set of demo data. Demo card 1234567890 has PIN 1234; the stored
-- pin_hash and pin_salt below are the pair for that PIN. Staff login is admin / 1234.
--
-- The atm_cash figures are the stocked float, USD 690,000 in total. After a withdrawal the
-- machine holds less, so restock it to these numbers to get back to a known state.
--
-- Regenerate with:
--   mysqldump -u<user> -p --no-create-info --complete-insert bankmanagementsystem_db \
--       customers accounts cards account_limits billers atm_cash admin_users
--
-- Run it against the same database name as schema-v2.sql.

CREATE DATABASE IF NOT EXISTS `bankmanagementsystem_db`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bankmanagementsystem_db`;



INSERT INTO `customers` (`id`, `cardno`, `name`, `fname`, `dob`, `gender`, `email`, `marital`, `address`, `city`, `religion`, `category`, `income`, `education`, `occupation`, `pan`, `aadhar`, `phone`, `services`, `acctype`, `status`, `created_at`) VALUES (3,'1234567890','Card holder 7890','','','','','','','','','','','','','','','','','','ACTIVE','2026-09-29 13:49:55'),(4,'9999888877','Card holder 8877','','','','','','','','','','','','','','','','','','ACTIVE','2026-09-29 13:49:55');
INSERT INTO `accounts` (`account_no`, `cardno`, `type`, `balance`, `status`, `currency`, `opened_at`) VALUES ('100755717733','9999888877','SAVING',8000.00,'ACTIVE','USD','2026-09-29 13:49:55'),('100991636034','1234567890','SAVING',20000.00,'ACTIVE','USD','2026-09-29 13:49:55');
INSERT INTO `cards` (`cardno`, `account_no`, `pin_hash`, `pin_salt`, `status`, `failed_attempts`, `locked_until`, `expires_on`, `issued_at`, `last_used_at`) VALUES ('1234567890','100991636034','pbkdf2$120000$5Q+KGkBjb9SXGYdwmPsOwQ==$DRsIRxyqN1E6STYEYImQQEyUBcOaUE8us9LUZDGo5gM=','5Q+KGkBjb9SXGYdwmPsOwQ==','ACTIVE',0,NULL,'2029-09-29','2026-09-29 13:49:55','2026-09-29 15:07:27'),('9999888877','100755717733','pbkdf2$120000$ZohFGJecEu6ISbsDtL604w==$Pv0/Apa2K0mlKStszU+6xx5lhKXSn2aW58ZwXv9oOC0=','ZohFGJecEu6ISbsDtL604w==','ACTIVE',0,NULL,'2029-09-29','2026-09-29 13:49:56',NULL);
INSERT INTO `account_limits` (`account_no`, `per_txn`, `daily_withdrawal`, `daily_transfers`, `daily_bill_payments`, `require_otp_above`, `updated_at`) VALUES ('100755717733',10000.00,40000.00,5,10,5000.00,'2026-09-29 13:49:56'),('100991636034',10000.00,40000.00,5,10,5000.00,'2026-09-29 13:49:55');
INSERT INTO `billers` (`id`, `name`, `category`, `customer_ref_label`, `active`) VALUES (1,'ZESA Electricity','Utilities','Meter Number',1),(2,'Zimtel Broadband','Utilities','Account Number',1),(3,'Liquid Telecom','Telecom','Phone Number',1),(4,'NetOne Data Bundle','Telecom','Phone Number',1),(5,'City Council Rates','Utilities','Account Number',1),(6,'StarLife Insurance','Insurance','Policy Number',1);
INSERT INTO `atm_cash` (`denom`, `notes`, `updated_at`) VALUES (20,2000,'2026-09-29 13:49:55'),(50,1000,'2026-09-29 13:49:55'),(100,1000,'2026-09-29 13:49:55'),(200,500,'2026-09-29 13:49:55'),(500,200,'2026-09-29 13:49:55'),(1000,100,'2026-09-29 13:49:55'),(2000,50,'2026-09-29 13:49:55'),(5000,20,'2026-09-29 13:49:55');
INSERT INTO `admin_users` (`username`, `full_name`, `role`, `pin_hash`, `pin_salt`, `status`, `last_login`, `failed_attempts`, `locked_until`) VALUES ('admin','Branch Manager','ADMIN','pbkdf2$120000$T1hqteLOosKRljvtXGd/7Q==$9rzJaHOz/9bRjXIbRWqzA9Z8WzCEmWfwp/xDPLhQhLM=','T1hqteLOosKRljvtXGd/7Q==','ACTIVE','2026-09-29 15:07:30',0,NULL);
