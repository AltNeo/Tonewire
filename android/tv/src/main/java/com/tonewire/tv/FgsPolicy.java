package com.tonewire.tv;

import android.content.pm.ServiceInfo;

/** SDK-specific foreground-service type selection used by TvEqualizerService. */
final class FgsPolicy {
    private FgsPolicy() {}

    static int typeForSdk(int sdk) {
        if (sdk >= 34) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        }
        return 0;
    }
}
