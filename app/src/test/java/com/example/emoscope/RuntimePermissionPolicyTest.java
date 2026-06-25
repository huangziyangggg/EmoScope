package com.example.emoscope;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RuntimePermissionPolicyTest {

    @Test
    public void skipsRequestWhenPermissionIsAlreadyGranted() {
        assertEquals(RuntimePermissionPolicy.NextAction.ALREADY_GRANTED,
                RuntimePermissionPolicy.nextAction(true, false));
    }

    @Test
    public void showsRationaleOnlyForDeniedPermissionThatCanBeExplained() {
        assertEquals(RuntimePermissionPolicy.NextAction.SHOW_RATIONALE,
                RuntimePermissionPolicy.nextAction(false, true));
    }

    @Test
    public void requestsSystemPermissionWhenNoRationaleIsNeeded() {
        assertEquals(RuntimePermissionPolicy.NextAction.REQUEST_SYSTEM_PERMISSION,
                RuntimePermissionPolicy.nextAction(false, false));
    }
}
