package hudson.plugins.ec2.monitoring;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for EC2ProvisioningMonitor.
 */
public class EC2ProvisioningMonitorTest {
    
    @Test
    public void testProvisioningEventCreation() {
        ProvisioningEvent event = new ProvisioningEvent(
            "us-west-2",
            "us-west-2a", 
            "test-request-123",
            "m5.large",
            2,
            1,
            1,
            "test-controller",
            "test-cloud",
            "REQUEST",
            null,
            "https://jenkins.example.com/"
        );
        
        assertEquals("us-west-2", event.getRegion());
        assertEquals("us-west-2a", event.getAvailabilityZone());
        assertEquals("test-request-123", event.getRequestId());
        assertEquals("m5.large", event.getRequestedInstanceType());
        assertEquals(2, event.getRequestedMaxCount());
        assertEquals(1, event.getRequestedMinCount());
        assertEquals(1, event.getProvisionedInstancesCount());
        assertEquals("test-controller", event.getControllerName());
        assertEquals("test-cloud", event.getCloudName());
        assertEquals("REQUEST", event.getPhase());
        assertNull(event.getErrorMessage());
        assertEquals("https://jenkins.example.com/", event.getJenkinsUrl());
        assertNotNull(event.getTimestamp());
    }
    
    @Test
    public void testRecordProvisioningEvent() {
        // This is mainly to ensure the method doesn't throw exceptions
        ProvisioningEvent event = new ProvisioningEvent(
            "us-east-1",
            "us-east-1a",
            "test-123", 
            "t3.micro",
            1,
            1,
            0,
            "jenkins-controller",
            "ec2-cloud",
            "REQUEST",
            null,
            "https://jenkins.test.com/"
        );
        
        // Should not throw exception
        EC2ProvisioningMonitor.recordProvisioningEvent(event);
        
        // Test with error
        ProvisioningEvent errorEvent = new ProvisioningEvent(
            "us-east-1",
            "us-east-1a",
            "test-456",
            "t3.micro", 
            1,
            1,
            0,
            "jenkins-controller",
            "ec2-cloud",
            "FAILURE",
            "Test error message",
            "https://jenkins.test.com/"
        );
        
        // Should not throw exception
        EC2ProvisioningMonitor.recordProvisioningEvent(errorEvent);
    }
} 