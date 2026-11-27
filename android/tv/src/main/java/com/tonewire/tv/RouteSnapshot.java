package com.tonewire.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Connected outputs; equivalent descriptors share a route; distinct outputs remain ambiguous. */
public final class RouteSnapshot {
    private final List<AudioRoute> routes;
    private final AudioRoute selectedRoute;

    public RouteSnapshot(List<AudioRoute> routes) {
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        AudioRoute candidate = null;
        for (AudioRoute route : routes) {
            if (candidate == null) candidate = route;
            else if (!candidate.getFingerprint().equals(route.getFingerprint())) {
                candidate = null;
                break;
            } else if (route.getDeviceId() < candidate.getDeviceId()) candidate = route;
        }
        this.selectedRoute = candidate;
    }
    public List<AudioRoute> getRoutes() { return routes; }
    public AudioRoute getSelectedRoute() { return selectedRoute; }
    public boolean isAmbiguous() { return selectedRoute == null; }
}
