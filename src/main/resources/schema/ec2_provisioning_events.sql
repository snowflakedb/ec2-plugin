-- EC2 Provisioning Events table schema for Snowflake
-- This file is for documentation purposes only - the table should be created manually in Snowflake

CREATE OR REPLACE TABLE EC2_PROVISIONING_EVENTS (
    CREATE_TIME TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP(),
    REGION STRING,
    AVAILABILITY_ZONE STRING,
    REQUEST_ID STRING,
    REQUESTED_INSTANCE_TYPE STRING,
    REQUESTED_MAX_COUNT NUMBER,
    REQUESTED_MIN_COUNT NUMBER,
    PROVISIONED_INSTANCES_COUNT NUMBER,
    CONTROLLER_NAME STRING,
    PHASE STRING,  -- REQUEST, SUCCESS, FAILURE
    ERROR_MESSAGE STRING,
    JENKINS_URL STRING,
    EVENT_DATA VARIANT  -- Full JSON event data for flexibility
);

-- Create automatic table stage for bulk loading
-- This will be created automatically when the plugin uploads data 