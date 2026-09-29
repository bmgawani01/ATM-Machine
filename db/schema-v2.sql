-- ATM System - database schema, version 2
--
-- Generated from a database the application bootstrapped itself, so this file is exactly what
-- the running code creates. The application also applies this schema on start, so loading this
-- file by hand is optional: pointing the app at an empty database is enough.
--
-- Regenerate with:
--   java -Datm.db.name=atm_v2_export -cp target/atm-system.jar atm.core.ScreenFlows
--   mysqldump -u<user> -p --no-data --skip-add-drop-table atm_v2_export > db/schema-v2.sql
--
-- Legacy tables kept for compatibility: bank, login, signup, signup2, signupthree.
-- The screens no longer read them; see db/seed-v2.sql for the data the app needs.

-- To install on a clean server:
--   mysql -u<user> -p < db/schema-v2.sql
--   mysql -u<user> -p < db/seed-v2.sql
-- The database name below matches the application default. To use another one, set
-- -Datm.db.name=<name> when starting the app instead of editing this file.

CREATE DATABASE IF NOT EXISTS `bankmanagementsystem_db`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bankmanagementsystem_db`;


/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account_limits` (
  `account_no` varchar(19) NOT NULL,
  `per_txn` decimal(15,2) DEFAULT NULL,
  `daily_withdrawal` decimal(15,2) DEFAULT NULL,
  `daily_transfers` int DEFAULT NULL,
  `daily_bill_payments` int DEFAULT NULL,
  `require_otp_above` decimal(15,2) DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `account_no` varchar(19) NOT NULL,
  `cardno` varchar(19) NOT NULL,
  `type` varchar(20) NOT NULL DEFAULT 'SAVING',
  `balance` decimal(15,2) NOT NULL DEFAULT '0.00',
  `status` varchar(12) NOT NULL DEFAULT 'ACTIVE',
  `currency` char(3) DEFAULT 'USD',
  `opened_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_no`),
  KEY `ix_accounts_cardno` (`cardno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_users` (
  `username` varchar(32) NOT NULL,
  `full_name` varchar(60) DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'TELLER',
  `pin_hash` varchar(160) NOT NULL,
  `pin_salt` varchar(64) NOT NULL,
  `status` varchar(12) NOT NULL DEFAULT 'ACTIVE',
  `last_login` timestamp NULL DEFAULT NULL,
  `failed_attempts` int NOT NULL DEFAULT '0',
  `locked_until` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `airtime_purchases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_no` varchar(19) NOT NULL,
  `network` varchar(30) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `txn_id` bigint DEFAULT NULL,
  `reference` varchar(40) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `atm_cash` (
  `denom` int NOT NULL,
  `notes` int NOT NULL DEFAULT '0',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`denom`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor` varchar(32) DEFAULT NULL,
  `cardno` varchar(19) DEFAULT NULL,
  `action` varchar(40) NOT NULL,
  `details` varchar(255) DEFAULT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_audit_action` (`action`,`created_at`),
  KEY `ix_audit_card` (`cardno`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bank` (
  `pin` varchar(16) DEFAULT NULL,
  `date` varchar(80) DEFAULT NULL,
  `mode` varchar(20) DEFAULT NULL,
  `amount` varchar(40) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bill_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_no` varchar(19) NOT NULL,
  `biller_id` bigint NOT NULL,
  `customer_ref` varchar(60) DEFAULT NULL,
  `amount` decimal(15,2) NOT NULL,
  `txn_id` bigint DEFAULT NULL,
  `reference` varchar(40) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(60) NOT NULL,
  `category` varchar(40) NOT NULL,
  `customer_ref_label` varchar(40) DEFAULT NULL,
  `active` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cards` (
  `cardno` varchar(19) NOT NULL,
  `account_no` varchar(19) NOT NULL,
  `pin_hash` varchar(160) NOT NULL,
  `pin_salt` varchar(64) NOT NULL,
  `status` varchar(12) NOT NULL DEFAULT 'ACTIVE',
  `failed_attempts` int NOT NULL DEFAULT '0',
  `locked_until` timestamp NULL DEFAULT NULL,
  `expires_on` date DEFAULT NULL,
  `issued_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`cardno`),
  KEY `ix_cards_account` (`account_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cardno` varchar(19) NOT NULL,
  `name` varchar(60) NOT NULL,
  `fname` varchar(60) DEFAULT NULL,
  `dob` varchar(30) DEFAULT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `email` varchar(80) DEFAULT NULL,
  `marital` varchar(12) DEFAULT NULL,
  `address` varchar(150) DEFAULT NULL,
  `city` varchar(40) DEFAULT NULL,
  `religion` varchar(20) DEFAULT NULL,
  `category` varchar(20) DEFAULT NULL,
  `income` varchar(30) DEFAULT NULL,
  `education` varchar(20) DEFAULT NULL,
  `occupation` varchar(20) DEFAULT NULL,
  `pan` varchar(30) DEFAULT NULL,
  `aadhar` varchar(30) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `services` varchar(200) DEFAULT NULL,
  `acctype` varchar(30) DEFAULT NULL,
  `status` varchar(16) DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customers_cardno` (`cardno`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `failed_logins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cardno` varchar(19) DEFAULT NULL,
  `reason` varchar(40) DEFAULT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_failed_card` (`cardno`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login` (
  `cardno` varchar(19) NOT NULL,
  `pin` varchar(32) NOT NULL,
  PRIMARY KEY (`cardno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `otp_challenges` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cardno` varchar(19) NOT NULL,
  `purpose` varchar(32) NOT NULL,
  `code_hash` varchar(160) NOT NULL,
  `code_salt` varchar(64) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `consumed_at` timestamp NULL DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_otp_card` (`cardno`,`purpose`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sessions` (
  `id` varchar(64) NOT NULL,
  `cardno` varchar(19) DEFAULT NULL,
  `account_no` varchar(19) DEFAULT NULL,
  `channel` varchar(12) DEFAULT 'DESKTOP',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `revoked` tinyint NOT NULL DEFAULT '0',
  `revoked_reason` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signup` (
  `formno` varchar(16) NOT NULL,
  `name` varchar(60) DEFAULT NULL,
  `fname` varchar(60) DEFAULT NULL,
  `dob` varchar(40) DEFAULT NULL,
  `gender` varchar(16) DEFAULT NULL,
  `email` varchar(80) DEFAULT NULL,
  `marital` varchar(20) DEFAULT NULL,
  `address` varchar(150) DEFAULT NULL,
  `city` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`formno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signup2` (
  `formno` varchar(16) NOT NULL,
  `religion` varchar(20) DEFAULT NULL,
  `category` varchar(20) DEFAULT NULL,
  `income` varchar(30) DEFAULT NULL,
  `education` varchar(20) DEFAULT NULL,
  `occupation` varchar(20) DEFAULT NULL,
  `pan` varchar(30) DEFAULT NULL,
  `aadhar` varchar(30) DEFAULT NULL,
  `scitizen` varchar(10) DEFAULT NULL,
  `eaccount` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`formno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signupthree` (
  `formno` varchar(16) NOT NULL,
  `acctype` varchar(30) DEFAULT NULL,
  `services` varchar(200) DEFAULT NULL,
  `e_account` varchar(10) DEFAULT NULL,
  `pin` varchar(16) DEFAULT NULL,
  PRIMARY KEY (`formno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_no` varchar(19) NOT NULL,
  `type` varchar(24) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `balance_after` decimal(15,2) NOT NULL,
  `channel` varchar(12) NOT NULL DEFAULT 'ATM',
  `note` varchar(150) DEFAULT NULL,
  `reference` varchar(40) DEFAULT NULL,
  `idempotency_key` varchar(64) DEFAULT NULL,
  `status` varchar(12) NOT NULL DEFAULT 'SUCCESS',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `ix_txn_account_date` (`account_no`,`created_at`),
  KEY `ix_txn_type_date` (`type`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_account` varchar(19) NOT NULL,
  `to_account` varchar(19) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `from_ref` varchar(40) DEFAULT NULL,
  `to_ref` varchar(40) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
