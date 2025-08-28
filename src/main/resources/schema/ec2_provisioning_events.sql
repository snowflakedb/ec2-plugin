-- EC2 Provisioning Events hybrid table schema for Snowflake
-- This file is for documentation purposes only - the table should be created manually in Snowflake

CREATE OR REPLACE HYBRID TABLE EC2_PROVISIONING_EVENTS (
    ID NUMBER AUTOINCREMENT,
    CREATE_TIME TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP(),
    REGION VARCHAR(50),
    AVAILABILITY_ZONE VARCHAR(50),
    REQUEST_ID VARCHAR(100),
    REQUESTED_INSTANCE_TYPE VARCHAR(50),
    REQUESTED_MAX_COUNT NUMBER,
    REQUESTED_MIN_COUNT NUMBER,
    PROVISIONED_INSTANCES_COUNT NUMBER,
    CONTROLLER_NAME VARCHAR(200),
    PHASE VARCHAR(50),  -- REQUEST, SUCCESS, FAILURE, REQUEST_FALLBACK, SUCCESS_FALLBACK
    ERROR_MESSAGE VARCHAR(2000),
    JENKINS_URL VARCHAR(500),
    EVENT_DATA VARIANT,  -- Full JSON event data for flexibility
    PRIMARY KEY (ID)
);

-- Create automatic table stage for bulk loading
-- This will be created automatically when the plugin uploads data

-- Note: Hybrid tables provide:
-- - Faster point lookups and range scans
-- - ACID transactions with row-level locking
-- - Better performance for real-time analytics
-- - Support for unique constraints and foreign keys 