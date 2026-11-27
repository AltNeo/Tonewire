package com.tonewire.tv;

import android.content.pm.ServiceInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FgsPolicyTest {
    @Test
    public void api31UsesNoServiceType() {
        assertEquals(0, FgsPolicy.typeForSdk(31));
    }

    @Test
    public void api33UsesNoServiceType() {
        assertEquals(0, FgsPolicy.typeForSdk(33));
    }

    @Test
    public void api34UsesPlatformSpecialUseType() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, FgsPolicy.typeForSdk(34));
    }

    @Test
    public void api35UsesPlatformSpecialUseType() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, FgsPolicy.typeForSdk(35));
    }

    @Test
    public void api36UsesPlatformSpecialUseType() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, FgsPolicy.typeForSdk(36));
    }
}
