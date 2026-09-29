-- ATM System - full schema for a MySQL-compatible cloud database
-- (TiDB Cloud Serverless / Aiven / any MySQL 8 host)
--
-- Run it once, or skip it: atm.system.Conn creates these same tables automatically
-- the first time the app connects (CREATE TABLE IF NOT EXISTS), so the app also
-- works against a completely empty cloud database.

CREATE DATABASE IF NOT EXISTS bankmanagementsystem_db
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE bankmanagementsystem_db;

CREATE TABLE IF NOT EXISTS login (
  cardno VARCHAR(16) NOT NULL PRIMARY KEY,
  pin    VARCHAR(16) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS signup (
  formno  VARCHAR(16) NOT NULL PRIMARY KEY,
  name    VARCHAR(40),
  fname   VARCHAR(40),
  dob     VARCHAR(40),
  gender  VARCHAR(16),
  email   VARCHAR(60),
  marital VARCHAR(20),
  address VARCHAR(120),
  city    VARCHAR(40)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS signup2 (
  formno     VARCHAR(16) NOT NULL PRIMARY KEY,
  religion   VARCHAR(20),
  category   VARCHAR(20),
  income     VARCHAR(30),
  education  VARCHAR(20),
  occupation VARCHAR(20),
  pan        VARCHAR(30),
  aadhar     VARCHAR(30),
  scitizen   VARCHAR(10),
  eaccount   VARCHAR(10)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS signupthree (
  formno   VARCHAR(16) NOT NULL PRIMARY KEY,
  acctype  VARCHAR(30),
  services VARCHAR(200),
  e_account VARCHAR(10),
  pin      VARCHAR(16)
) ENGINE=InnoDB;

-- Columns and order matter: the app inserts with INSERT INTO bank VALUES (...) (no column list).
CREATE TABLE IF NOT EXISTS bank (
  pin    VARCHAR(16),
  date   VARCHAR(80),
  mode   VARCHAR(20),
  amount VARCHAR(40)
) ENGINE=InnoDB;
