CREATE TABLE IF NOT EXISTS alignmentconfig (
    name varchar NOT NULL,
    sourceontologyuri varchar NOT NULL,
    targetontologyuri varchar NOT NULL,
    version varchar NOT NULL,
    creator varchar NOT NULL,
    description varchar NOT NULL,
    xmlsource varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS alignmentdata (
    id integer PRIMARY KEY AUTOINCREMENT,
    date integer NOT NULL,
    name varchar NOT NULL,
    sourceontologyuri varchar NOT NULL,
    targetontologyuri varchar NOT NULL,
    version varchar NOT NULL,
    creator varchar NOT NULL,
    description varchar NOT NULL,
    xmlsource varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS alignmentinfo (
    id integer PRIMARY KEY AUTOINCREMENT,
    date integer NOT NULL,
    name varchar NOT NULL,
    sourceontologyuri varchar NOT NULL,
    targetontologyuri varchar NOT NULL,
    version varchar NOT NULL,
    creator varchar NOT NULL,
    descid varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS channelconfig (
    chantype varchar CHECK( chantype IN ('MM', 'KK') ) NOT NULL,
    source varchar NOT NULL,
    inpalignmentname varchar NOT NULL,
    inpalignmentversion varchar NOT NULL,
    outalignmentname varchar NOT NULL,
    outalignmentversion varchar NOT NULL,
    sink varchar NOT NULL,
    optconsumergroup varchar,
    optparallelism integer
);

CREATE TABLE IF NOT EXISTS channel (
    id integer PRIMARY KEY AUTOINCREMENT,
    chantype varchar CHECK( chantype IN ('MM', 'KK') ) NOT NULL,
    source varchar NOT NULL,
    inpalignmentname varchar NOT NULL,
    inpalignmentversion varchar NOT NULL,
    outalignmentname varchar NOT NULL,
    outalignmentversion varchar NOT NULL,
    sink varchar NOT NULL,
    consumergroup varchar NOT NULL,
    parallelism integer NOT NULL,
    uuid varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS channelinfo (
    id integer PRIMARY KEY AUTOINCREMENT,
    chantype varchar CHECK( chantype IN ('MM', 'KK') ) NOT NULL,
    source varchar NOT NULL,
    inpalignmentname varchar NOT NULL,
    inpalignmentversion varchar NOT NULL,
    outalignmentname varchar NOT NULL,
    outalignmentversion varchar NOT NULL,
    sink varchar NOT NULL,
    consumergroup varchar NOT NULL,
    parallelism integer NOT NULL,
    uuid varchar NOT NULL,
    descid varchar NOT NULL
);
